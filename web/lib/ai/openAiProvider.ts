import OpenAI from "openai";
import { GatewayError, statusOf } from "./errors";
import { SYSTEM_INSTRUCTION, userMessage } from "./prompt";
import { TOOLS } from "./tools";

export async function processWithOpenAI(
  apiKey: string,
  query: string,
  context: Record<string, string>
) {
  const openai = new OpenAI({ apiKey });

  let message;
  try {
    const response = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        { role: "system", content: SYSTEM_INSTRUCTION },
        { role: "user", content: userMessage(query, context) },
      ],
      tools: TOOLS.map((tool) => ({
        type: "function" as const,
        function: {
          name: tool.name,
          description: tool.description,
          parameters: tool.parameters as Record<string, unknown>,
        },
      })),
      temperature: 0.2,
    });
    message = response.choices[0]?.message;
  } catch (error: unknown) {
    const status = statusOf(error);
    console.error(`OpenAI API error (status ${status ?? "none"})`);
    if (status === 429 || status === 503) {
      throw new GatewayError("provider_busy", "The AI provider is rate limiting requests.");
    }
    throw new GatewayError("provider_unavailable", "The AI provider request failed.");
  }

  if (!message) throw new GatewayError("invalid_provider_response", "The AI provider returned no message.");

  const toolCalls = [];
  for (const call of message.tool_calls ?? []) {
    if (call.type !== "function") continue;
    try {
      toolCalls.push({ name: call.function.name, args: JSON.parse(call.function.arguments) });
    } catch {
      // Malformed arguments are the provider's fault, not an outage - report it as such
      // rather than letting a JSON error surface as "provider unavailable".
      throw new GatewayError("invalid_provider_response", "The AI provider returned malformed tool arguments.");
    }
  }
  return { toolCalls, text: message.content || "" };
}
