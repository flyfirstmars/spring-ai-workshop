# Workshop Exercises

Use these exercises during the first two sessions to reinforce VoyagerMate concepts. They are grouped by session but can be blended for different skill levels.

## Session 1 — Augmented LLM
1. **Text Concierge Enhancements**
   - Extend the `chat` command to accept traveller profile fields (age range, accessibility needs) and inject them into the prompt.
   - Expose latency metrics via Micrometer and log the response metadata for shell output.
2. **Multimodal Mastery**
   - Update `describe-image` to accept multiple files and compare suggestions side-by-side.
   - Enhance `transcribe-audio` to auto-detect WAV or MP3 MIME types without flags.
3. **Structured Output Builder**
   - Introduce a `visaNotes` field in `ItineraryPlan` populated from a new `@Tool`.
   - Validate in unit tests that the LLM fills every day in the itinerary before printing to the shell.
4. **Travel Tools**
   - Implement a `@Tool` that recommends public transit passes by city.
   - Add graceful fallbacks when tools throw exceptions or return empty data, surfacing hints in shell output.

## Session 2 — Agents & Workflows
1. **Workflow Branching**
   - Modify `ItineraryWorkflowService` to spawn a parallel call that drafts dining reservations.
   - Combine the results into a richer `TripWorkflowSummary` and display via `workflow`.
2. **Agent Prompt Engineering**
   - Encourage VoyagerMate to ask clarifying questions before finalising plans in `plan-itinerary`.
   - Track which tools were invoked and add a summary section to the command output.
3. **Budget Intelligence**
   - Expand `VoyagerTools.estimateBudget` to account for traveller count and hotel class.
   - Surface a cost breakdown in `plan-itinerary` and format currency per locale.
4. **Voice Concierge**
   - Build a scheduled job that polls recorded audio notes, calls `transcribe-audio`, and stores summaries.
   - Notify participants when transcription confidence is low and a manual follow-up is recommended.
