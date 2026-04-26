

<div align="center">

<img src="https://media.tenor.com/CTSpEQfoZzoAAAAM/project-hail-mary-rocky-project-hail-mary.gif" width="150" px />

</div>

# JetFlow: CI/CD Management System for IntelliJ
---
JetFlow is an advanced plugin for the IntelliJ platform designed to centralize and simplify Continuous Integration and Continuous Deployment (CI/CD) operations. The system allows developers to manage cloud infrastructure and automation workflows without leaving the integrated development environment.

## Rocky Assistant
---
Rocky is a contextual assistance component integrated into the IDE interface. Its primary function is to guide the user through initial configuration and maintenance tasks.

* **Assisted Configuration:** Provides guidance in creating configuration files for various environments.
* **Status Notifications:** Offers visual feedback regarding the success or failure of deployment operations.
* **Quick Access:** Serves as a dynamic entry point for the plugin's most frequently used functions.

## Key Features
---
### Continuous Integration (CI) Automation
* **Workflow Generation:** Automatic creation of YAML files for GitHub Actions.
* **Stack Detection:** Suggests CI templates based on the technology detected in the project (Java, Kotlin, Python, etc.).
* **Validation:** Verifies the structure of configuration files before implementation.

### Render.com Integration (CD)
* **Service Management:** Direct connection with the Render API to monitor Web Services and Workers.
* **Manual Deployment:** Ability to trigger builds and deployments instantaneously.
* **History and Rollback:** Access to previous versions for rapid reversion in case of production errors.

### Context Generator (Code Bundler)
* **File Consolidation:** Groups source code and project structure into a single Markdown document.
* **AI Optimization:** Designed to facilitate context transfer to Large Language Models (LLMs).
* **Exclusion Control:** Automatically filters binary files, dependencies, and sensitive data.

## Installation
---
### System Requirements
* IntelliJ IDEA version 2023.1 or higher.
* Java Runtime Environment (JRE) compatible with the IDE version.

### Manual Installation Instructions

---

## Credential Configuration
---
For the correct operation of external integrations, access tokens must be configured in the JetFlow side panel:

1.  **GitHub PAT:** A Personal Access Token with read and write permissions for workflows is required to manage GitHub Actions.
2.  **Render API Key:** Necessary for communication with the Render.com control panel.

All secrets are stored securely using the IntelliJ platform's encrypted persistence system.

## Project Structure
---
The source code is organized as follows:

* **src/main/kotlin/.../auth:** Implementation of security and credential persistence.
* **src/main/kotlin/.../ci:** Logic for generating and managing GitHub workflows.
* **src/main/kotlin/.../cd:** API client and deployment services for Render.
* **src/main/kotlin/.../ui:** User interface components and Rocky assistant logic.
