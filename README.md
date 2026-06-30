# Smart Petition Generator

Smart Petition Generator is a native Android mobile application developed as a computer engineering graduation project[cite: 1]. It serves as a document automation system designed to simplify the creation, editing, storage, and export of formal petitions[cite: 1]. The system addresses the complexity of drafting structured documents by combining artificial intelligence, dynamic form generation, and local persistence into a unified mobile workflow[cite: 1].

### 🌟 Core Features

* **AI-Assisted Template Generation:** Interprets natural language requests sent to a backend service and returns a structured petition template alongside required input fields[cite: 1].
* **OCR-Based Preview:** Processes document images through an OCR layout analysis endpoint, detecting text lines and converting them into an editable preview[cite: 1].
* **Dynamic Form Construction:** Builds input fields dynamically at runtime by extracting placeholders from templates, allowing users to enter only the missing information[cite: 1].
* **Local Profile Management & Auto-Fill:** Securely stores user data (such as name, identity number, and address) on the device and automatically populates matching fields in the dynamic forms[cite: 1].
* **A4 Document Rendering & Editing:** Wraps the generated petition content in print-safe HTML and CSS, displaying it inside a WebView as an editable A4 page[cite: 1].
* **PDF Export:** Utilizes the native Android print framework to export or print the final reviewed document as a standard PDF[cite: 1].
* **Reusable Templates & History:** Saves generated or edited petition structures locally (after removing personal data) for future reuse, alongside a comprehensive local history of all generated documents[cite: 1].

### 🛠 Architecture & Tech Stack

The application is built with a modular architecture, strictly separating the user interface, state management, local data persistence, and remote API communication[cite: 1].

* **UI & Language:** Developed entirely in Kotlin, utilizing Jetpack Compose for a modern, declarative user interface[cite: 1].
* **Architecture Pattern:** Follows the MVVM (Model-View-ViewModel) structure[cite: 1]. The UI screens observe state from a shared `PetitionViewModel`, which coordinates workflows such as AI generation, dynamic form handling, and document formatting[cite: 1].
* **Local Persistence:** Uses the Room Database to handle local, offline storage for user profiles, ready templates, and petition history through Data Access Objects (DAOs)[cite: 1].
* **Network & API Communication:** Integrates Retrofit and OkHttp for JSON-based REST API calls[cite: 1]. 
* **Asynchronous Processing:** Implements a ticket-based polling mechanism for long-running AI and OCR backend operations, ensuring the Android client remains responsive and the UI is not blocked during execution[cite: 1].
* **Security:** Supports optional HMAC request signing with timestamp and signature headers for authenticated backend communication[cite: 1].
