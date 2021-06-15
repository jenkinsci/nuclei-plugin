# Vulnerability scanning using Nuclei

Fast and customisable vulnerability scanner based on simple YAML based DSL.

## How it works
* The plugin downloads the latest release of Nuclei from GitHub, based on the build executor's operating system and architecture
* The downloaded artifact is uncompressed 
* Nuclei Templates are downloaded/updated
* Scan is executed using the provided user-input

## Usage
* Create or edit a **Freestyle** project
* Add a **Nuclei Vulnerability Scanner** build step
* Introduce the URL of the target web application you intend to test
* Optionally:
    * add reporting configuration that allows automatic issue creation on platforms like Jira and GitHub
    * add additional CLI arguments (e.g. -v, -debug)
    
## Limitations
* Freestyle project support only (no pipelines)
* No bundled scanner binary, so the agents require internet access

## Nuclei documentation
* [https://nuclei.projectdiscovery.io](https://nuclei.projectdiscovery.io)
* [https://github.com/projectdiscovery/nuclei](https://github.com/projectdiscovery/nuclei)
