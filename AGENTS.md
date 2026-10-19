# AGENTS.md
I want a deeper version of this task, not a bigger one. Before rewriting it: (1) say what a top professional in this field would do here that I'm not asking for; (2) name the rawest material they would start from instead of what has already been digested for me; (3) name the specific methods and tools they would use; (4) say what, other than you or me, would show the result is right. Then rewrite my prompt to ask for exactly that.

# Coding standards
- **TDD (Test-Driven Development)** - write the tests first; the implementation code isn't done until the tests pass.

DRY (Don't Repeat Yourself) – eliminate duplicated logic by extracting shared utilities and modules.
Separation of Concerns – each module should handle one distinct responsibility.
Single Responsibility Principle (SRP) – every class/module/function/file should have exactly one reason to change.
Clear Abstractions & Contracts – expose intent through small, stable interfaces and hide implementation details.
Low Coupling, High Cohesion – keep modules self-contained, minimize cross-dependencies.
Scalability & Statelessness – design components to scale horizontally and prefer stateless services when possible.
Observability & Testability – build in logging, metrics, tracing, and ensure components can be unit/integration tested.
KISS (Keep It Simple, Sir) - keep solutions as simple as possible.
YAGNI (You're Not Gonna Need It) – avoid speculative complexity or over-engineering.

## 1. Interaction & Workflow
* **User Persona:** Assume the user is a mobile app developer (4 years of professional working experience main stack Flutter) and have some knowledge on backend (consider him a junior backend engineer, Python, FastAPI). He currently wants to expand his capabilities so he's not locked into one specific framework or tool like Flutter. So feel free to pick good technology for the project as you are the top professional.
* **Clarifications:** If a requirement is ambiguous, clarify the intended functionality and target platforms (e.g., Android, iOS, Web, Desktop) before implementing.
* **Differenct tech stack:** If you pick a different tech stack from the user main tech stack when coding the project, do it in such a way that it is digestable (he has to be able to understand the structure of the project, the way the framework/tech stack works and be able to follow along, not just blindly accepting because of his previously stated goal so he fully understand the new framework/tech stack). You can generate good comments on files, or breaking down tasksso the breakdown teaches, not just for partitions, for example. The project should have a README.md file so new developers can read and immediately understand and follow like how to get the framework/tech stack to install, env configuration, commands, etc (You have to log all the important commands and add comment for each command)

## 2. Core Architectural Principles
* **SOLID Principles:** Apply SOLID design principles across classes, widgets, and services.
* **Declarative & Functional:** Prefer declarative patterns and concise, modern Dart idioms over imperative boilerplate.
* **Composition over Inheritance:** Build complex widgets and business logic through composition of smaller, specialized components.
* **Immutability:** Keep data models and UI components immutable (`StatelessWidget` preferred). Rebuild instead of mutating state.
* **Separation of Concerns:** Clearly isolate UI, business logic (ViewModel), state management, and data access layers.

# Operational Policies
- **Token Optimization & Request Economy**: You must act with extreme request efficiency. Minimize the overall number of API requests and tool invocations.
- **Batch Adjustments**: Before proposing changes or executing any code/file modifications, you must first gather comprehensive workspace context. 
- **Plan Mode**: Synthesize all required modifications into a single, cohesive, multi-step proposal. Execute the changes in batch workflows rather than sequential, isolated requests.