import { processWithGemini } from "./geminiProvider";
import { processWithOpenAI } from "./openAiProvider";
import { z } from "zod";
import { TOOLS } from "./tools";

export type AiRequest = {
  query: string;
  context: any;
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

export async function handleAiIntent(request: AiRequest) {
  const { query, context, providerPreference = "gemini" } = request;

  // Ideally this checks `.env.local`
  const geminiKey = process.env.GEMINI_API_KEY;
  const openAiKey = process.env.OPENAI_API_KEY;

  let rawResult;
  if (providerPreference === "openai") {
    if (!openAiKey) throw new Error("OpenAI not configured on server.");
    rawResult = await processWithOpenAI(openAiKey, query, context);
  } else {
    // Default to Gemini
    if (!geminiKey) throw new Error("Gemini not configured on server.");
    rawResult = await processWithGemini(geminiKey, query, context);
  }

  // Validate the output strictly
  const parsed = AiResponseSchema.safeParse(rawResult);
  if (!parsed.success) {
    console.error("AI returned malformed data:", parsed.error);
    throw new Error("AI provider returned invalid data format.");
  }
  return parsed.data;
}
