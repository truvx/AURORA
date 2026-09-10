import { GoogleGenAI } from "@google/genai";
import { TOOLS } from "./tools";

export async function processWithGemini(apiKey: string, query: string, context: any) {
  const ai = new GoogleGenAI({ apiKey });

  const systemInstruction = `You are AURORA, a music intelligence agent.
You help users find music, build queues, and play tracks.
Do NOT fabricate track IDs or URLs. Use the provided tools to search or interact.
If you are asked to play or add to queue, ensure you have the actual track ID. If you don't have it, search for it first.
Context:
${JSON.stringify(context)}
`;

  let attempts = 0;
  while (attempts < 3) {
    try {
      const response = await ai.models.generateContent({
        model: "gemini-flash-latest",
        contents: [{ role: "user", parts: [{ text: query }] }],
        config: {
          systemInstruction,
          tools: [{ functionDeclarations: TOOLS as any }],
          temperature: 0.2,
        },
      });

      const calls = response.functionCalls;
      if (calls && calls.length > 0) {
        return {
          toolCalls: calls.map(call => ({
            name: call.name,
            args: call.args
          })),
          text: response.text || ""
        };
      }

      return {
        text: response.text || "",
        toolCalls: []
      };
    } catch (error: any) {
      console.error("Gemini API Error (Attempt " + (attempts + 1) + "):", error);
      if (error?.status === 503 || error?.status === 429 || error?.message?.includes("503") || error?.message?.includes("429") || error?.message?.includes("overloaded") || error?.message?.includes("Quota")) {
        attempts++;
        if (attempts < 3) {
          console.log("Retrying in 12 seconds due to rate limit/overload...");
          await new Promise(res => setTimeout(res, 12000));
          continue;
        }
      }
      throw new Error("Provider unavailable or failed.");
    }
  }
  throw new Error("Provider unavailable or failed.");
}
