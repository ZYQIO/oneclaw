---
name: fetch-webpage
display_name: "Fetch Webpage"
description: "Fetch and summarize a webpage's content"
version: "1.0"
tools_required:
  - webfetch
parameters:
  - name: url
    type: string
    required: true
    description: "The URL of the webpage to fetch"
---

# Fetch Webpage

## Instructions

1. Use `webfetch` to fetch the URL: {{url}}
2. The tool returns the page content as Markdown.
3. Extract the main textual content, ignoring navigation menus, ads, and boilerplate.
4. Provide:
   - **Title**: The page title.
   - **Summary**: A concise summary of the main content (3-5 sentences).
   - **Key Information**: Any specific data, facts, or lists on the page.
   - **Source**: The URL fetched.
