import { NextResponse } from "next/server";
import { handleAiIntent } from "../../../../lib/ai/gateway";
import { z } from "zod";

const RequestSchema = z.object({
  query: z.string().min(1).max(1000),
  context: z.any().optional(),
  providerPreference: z.enum(["gemini", "openai"]).optional()
});

export async function POST(req: Request) {
  try {
    const body = await req.json();
    
    // Strict validation
    const parsed = RequestSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        { error: "Invalid request payload", details: parsed.error.issues },
        { status: 400 }
      );
    }

    const result = await handleAiIntent({
      query: parsed.data.query,
      context: parsed.data.context || {},
      providerPreference: parsed.data.providerPreference
    });

    return NextResponse.json(result);
  } catch (error: any) {
    console.error("AI Gateway Route Error:", error);
    return NextResponse.json(
      { error: error.message || "Internal server error" },
      { status: 500 }
    );
  }
}
