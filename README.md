# EditorEs-2

C++ syntax support library for EditorEs, ported from AndroidIDE (GPL-3.0).

## Modules

- `:lexers` — ANTLR grammars (C++) and the generated lexers.
- `:cpp-language` — sora `Language` for C++: analyzer, newline handler, symbol pairs.

## Build

```sh
./gradlew :lexers:build :cpp-language:assembleRelease
```

Requires JDK 17. No NDK, no secrets.

## License

GNU General Public License v3.0, see LICENSE.
