import { GoogleGenAI } from "@google/genai";
import { GatewayError, statusOf } from "./errors";
import { SYSTEM_INSTRUCTION, userMessage } from "./prompt";
import { TOOLS } from "./tools";

/** One retry, briefly, and only for a transient overload. */
const MAX_ATTEMPTS = 2;
const RETRY_DELAY_MS = 2_000;

export async function processWithGemini(
  apiKey: string,
  query: string,
  context: Record<string, string>
) {
  const ai = new GoogleGenAI({ apiKey });

  for (let attempt = 1; ; attempt += 1) {
    try {
      const response = await ai.models.generateContent({
        model: "gemini-flash-latest",
        contents: [{ role: "user", parts: [{ text: userMessage(query, context) }] }],
        config: {
          systemInstruction: SYSTEM_INSTRUCTION,
          // The SDK's declaration type is stricter than the JSON-schema literals in TOOLS,
          // which the gateway validates on the way back out instead.
          tools: [{ functionDeclarations: TOOLS as never }],
          temperature: 0.2,
        },
      });

      return {
        toolCalls: (response.functionCalls ?? []).map((call) => ({
          name: call.name,
          args: call.args,
        })),
        text: response.text || "",
      };
    } catch (error: unknown) {
      const status = statusOf(error);
      console.error(`Gemini API error (attempt ${attempt}, status ${status ?? "none"})`);

      // 429 is not retried here. It covers per-minute limits and exhausted daily quota
      // alike, and the old loop slept 24 seconds inside the request retrying quota that
      // could not come back until tomorrow.
      if (status === 429) throw new GatewayError("provider_busy", "The AI provider is rate limiting requests.");
      if (status === 503 && attempt < MAX_ATTEMPTS) {
        await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
        continue;
      }
      if (status === 503) throw new GatewayError("provider_busy", "The AI provider is overloaded.");
      throw new GatewayError("provider_unavailable", "The AI provider request failed.");
    }
  }
}
