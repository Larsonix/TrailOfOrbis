# Contributing to Trail of Orbis

Thanks for your interest in contributing! Here's how to get started.

## Prerequisites

- **Java 25** — The project uses Java 25 features. The Gradle toolchain auto-provisions this.
- **Hytale Server** — Needed to test in-game. The API is resolved from Maven automatically for compilation.

## Building

```bash
./gradlew clean build    # Full build + tests
```

The project uses the Gradle wrapper, so no separate Gradle installation is needed.

## Code Style

- **Event registration**: Use `EventRegistry.register()`, not `@EventHandler` annotations (annotations don't work in Hytale).
- **Logging**: Use `HytaleLogger.forEnclosingClass()` — never `System.out.println`.
- **Player identity**: Always use `UUID`, never store `PlayerRef` long-term.
- **Database**: Prepared statements only, never concatenate SQL. Tables are prefixed `rpg_`.
- **Manager pattern**: Each system has a Manager class, initialized in order during `onEnable`.

## Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Run `./gradlew clean build` and ensure all tests pass
5. Submit a PR with a clear description of what changed and why

## Reporting Issues

Open a GitHub Issue with:
- What you expected to happen
- What actually happened
- Steps to reproduce (if applicable)
- Server version and any other mods installed

## License

By contributing code, you agree that your contributions will be licensed under [LGPL-3.0](LICENSE).

Asset contributions (textures, models, icons) are licensed under [CC-BY-NC-SA-4.0](LICENSE-ASSETS).
