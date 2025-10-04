# Environment Setup

Follow these steps before delivering the "AI Native Product Development using SpringAI" workshop. VoyagerMate is the
running example, but the prep applies to any product you prototype during the sessions.

## 1. Azure OpenAI

- Provision an Azure OpenAI resource in a region that supports GPT-4o or GPT-4 deployments.
- Create a deployment for `gpt-4o` or `gpt-4o-mini` depending on quota.
- (Optional) add a Whisper/GPT-4o Transcribe deployment if you intend to run the audio exercise.
- Provide the endpoint URL and API key, then share the expected environment variables:
  ```bash
  export AZURE_OPENAI_ENDPOINT="https://<resource-name>.openai.azure.com"
  export AZURE_OPENAI_API_KEY="<api-key>"
  export AZURE_OPENAI_CHAT_DEPLOYMENT="gpt-4o-mini"
  export AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT="whisper-1"   # optional
  ```

## 2. Local Tooling

- Install Java 25 (Temurin builds recommended) and Maven 3.9+.
- Optional: install Docker Compose via Rancher Desktop for upcoming Postgres and vector store demos (Sessions 3–4).
- Verify connectivity: `./mvnw -q -DskipTests spring-boot:run` should start the service.

## 3. Workshop Assets

- Prepare a few royalty-free travel photos (JPEG/PNG) for the image interaction demo.
- Record short MP3/WAV voice notes describing travel goals for the audio exercise (skip if no transcription deployment
  is available).
- Update `VoyagerTools` with destinations relevant to your organisation or region.

## 4. IDE Setup

- Encourage attendees to clone the repo before the workshop.
- Share IntelliJ or VS Code run configurations; ensure HTTP client collections or Postman examples are distributed.

## 5. Troubleshooting Checklist

- 401/403 from Azure OpenAI → re-issue API key, confirm deployment name.
- Rate limits → reduce concurrency, adjust temperature, or stagger demos.
- SSL issues → ensure the JDK trusts Azure certificates or provide `javax.net.ssl.trustStore` instructions.
- Base64 decoding errors → remind attendees to avoid data URLs and ensure binary-safe encoding.
