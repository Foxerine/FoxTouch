# FoxTouch

An AI-powered phone agent for Android. FoxTouch observes the screen via Accessibility Services, understands UI elements, and performs actions on behalf of the user through natural language instructions.

> **Status: Proof of Concept / Work in Progress**
>
> This project is in early experimental stage. Features may be incomplete, unstable, or change significantly. Not intended for production use.

## How It Works

1. User describes a task in natural language
2. FoxTouch reads the screen using the Android Accessibility API
3. An LLM (configurable: OpenAI, Anthropic, Google, or OpenRouter) analyzes the UI and decides actions
4. FoxTouch executes actions (tap, type, scroll, swipe, etc.) via Accessibility Services
5. The observe-think-act cycle repeats until the task is complete

## Key Features

- **Multi-provider LLM support** — OpenAI, Anthropic, Google Gemini, OpenRouter
- **Screen understanding** — UI element tree parsing + screenshot annotation with coordinate grids
- **Full device interaction** — tap, type, scroll, swipe, long press, pinch, back/home navigation
- **Plan mode** — complex tasks get planned and reviewed before execution
- **Skill system** — save and reuse plans for recurring workflows
- **Task tracking** — multi-step progress visible to the user in real-time
- **Floating overlay** — control the agent from any app via a floating bubble
- **Built-in IME** — invisible input method for reliable text input across all apps
- **Safety guardrails** — payment/auth detection, approval prompts, risk-level permissions

## Requirements

- Android 8.0+ (API 26)
- Accessibility Service permission
- Overlay (draw over other apps) permission
- An API key for at least one supported LLM provider

## AI Authorship Disclosure

The vast majority of the code in this project was written by AI (Claude), with human direction, review, and iteration. This includes application architecture, UI implementation, tool system design, accessibility integration, and system prompts.

## License

All rights reserved. This is a private project not yet released under an open-source license.
