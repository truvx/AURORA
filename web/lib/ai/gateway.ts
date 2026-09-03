import { processWithGemini } from "./geminiProvider";
import { processWithOpenAI } from "./openAiProvider";
import { z } from "zod";

export type AiRequest = {
  query: string;
  context: any;
  providerPreference?: "gemini" | "openai";
};

// Define strict schema for expected AI responses
const ToolCallSchema = z.object({
  name: z.string(),
  args: z.record(z.string(), z.any())
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
