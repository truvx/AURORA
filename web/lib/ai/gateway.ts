import { processWithGemini } from "./geminiProvider";
import { processWithOpenAI } from "./openAiProvider";
import { z } from "zod";
import { GatewayError } from "./errors";
import { TOOLS } from "./tools";

export type AiRequest = {
  query: string;
  context: Record<string, string>;
  providerPreference?: "gemini" | "openai";
};

// The allowlist the model is actually offered, derived from the tool definitions so the
// two can never drift apart. A model that emits anything else is rejected at the gateway
// rather than relying on the client to ignore it.
const TOOL_NAMES = TOOLS.map((tool) => tool.name) as [string, ...string[]];

// Define strict schema for expected AI responses
const ToolCallSchema = z.object({
  name: z.enum(TOOL_NAMES),
  // Argument values are scalars or arrays of scalars; nested objects are not part of any
  // declared tool and would only widen what reaches the executor.
  args: z.record(
    z.string(),
    z.union([
      z.string(),
      z.number(),
      z.boolean(),
      z.array(z.union([z.string(), z.number()]))
    ])
  )
});

const AiResponseSchema = z.object({
  toolCalls: z.array(ToolCallSchema).optional(),
  text: z.string()
});

export type Providers = {
  gemini: typeof processWithGemini;
  openai: typeof processWithOpenAI;
};

export async function handleAiIntent(
  request: AiRequest,
  env: Record<string, string | undefined> = process.env,
  providers: Providers = { gemini: processWithGemini, openai: processWithOpenAI }
) {
  const { query, context, providerPreference = "gemini" } = request;

  let rawResult;
  if (providerPreference === "openai") {
    if (!env.OPENAI_API_KEY) throw new GatewayError("ai_not_configured", "OpenAI is not configured on the server.");
    rawResult = await providers.openai(env.OPENAI_API_KEY, query, context);
  } else {
    if (!env.GEMINI_API_KEY) throw new GatewayError("ai_not_configured", "Gemini is not configured on the server.");
    rawResult = await providers.gemini(env.GEMINI_API_KEY, query, context);
  }

  // Validate the output strictly
  const parsed = AiResponseSchema.safeParse(rawResult);
  if (!parsed.success) {
    console.error("AI returned malformed data:", parsed.error.issues.map((issue) => issue.path.join(".")));
    throw new GatewayError("invalid_provider_response", "The AI provider returned an unexpected response.");
  }
  return parsed.data;
}
