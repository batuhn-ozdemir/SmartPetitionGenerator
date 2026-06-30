# Smart Petition Generator

Smart Petition Generator is a native Android mobile application developed as a computer engineering graduation project. It serves as a document automation system designed to simplify the creation, editing, storage, and export of formal petitions. The system addresses the complexity of drafting structured documents by combining artificial intelligence, dynamic form generation, and local persistence into a unified mobile workflow.

### 🌟 Core Features

* **AI-Assisted Template Generation:** Interprets natural language requests sent to a backend service and returns a structured petition template alongside required input fields.
* **OCR-Based Preview:** Processes document images through an OCR layout analysis endpoint, detecting text lines and converting them into an editable preview.
* **Dynamic Form Construction:** Builds input fields dynamically at runtime by extracting placeholders from templates, allowing users to enter only the missing information.
* **Local Profile Management & Auto-Fill:** Securely stores user data (such as name, identity number, and address) on the device and automatically populates matching fields in the dynamic forms.
* **A4 Document Rendering & Editing:** Wraps the generated petition content in print-safe HTML and CSS, displaying it inside a WebView as an editable A4 page.
* **PDF Export:** Utilizes the native Android print framework to export or print the final reviewed document as a standard PDF.
* **Reusable Templates & History:** Saves generated or edited petition structures locally (after removing personal data) for future reuse, alongside a comprehensive local history of all generated documents.

### 🛠 Architecture & Tech Stack

The application is built with a modular architecture, strictly separating the user interface, state management, local data persistence, and remote API communication.

* **UI & Language:** Developed entirely in Kotlin, utilizing Jetpack Compose for a modern, declarative user interface.
* **Architecture Pattern:** Follows the MVVM (Model-View-ViewModel) structure. The UI screens observe state from a shared `PetitionViewModel`, which coordinates workflows such as AI generation, dynamic form handling, and document formatting.
* **Local Persistence:** Uses the Room Database to handle local, offline storage for user profiles, ready templates, and petition history through Data Access Objects (DAOs).
* **Network & API Communication:** Integrates Retrofit and OkHttp for JSON-based REST API calls. 
* **Asynchronous Processing:** Implements a ticket-based polling mechanism for long-running AI and OCR backend operations, ensuring the Android client remains responsive and the UI is not blocked during execution.
* **Security:** Supports optional HMAC request signing with timestamp and signature headers for authenticated backend communication.
