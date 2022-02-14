# mce

[![test](https://github.com/mcenv/mce/actions/workflows/test.yml/badge.svg)](https://github.com/mcenv/mce/actions/workflows/test.yml)

A programming environment[^1] for Minecraft[^2].

## Goals

```mermaid
flowchart TB
   LC[Logical consistency]   --> S[Safety]
   SE[Static expressivity]   --> AO[Aggressive optimization]
   SE                        --> S[Safety]
   AO                        --> HP[High performance]
   AO                        --> LF[Low footprint]
   OP[Output predictability] --> E[Ergonomics]
   FC[Fast compilation]      --> E
   S                         --> F[Fun]
   HP                        --> F
   LF                        --> F
   E                         --> F
```

## Features

- [ ] Projectional editing
- [x] Dependent types
- [ ] Extensional types
- [x] Structural types
- [ ] Occurrence types
- [x] Two-phase types
- [x] Subtyping
- [x] Effect system
- [ ] Cost analysis
- [x] Staging

[^1]: A highly integrated pair of a programming language and a development environment.
[^2]: NOT OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG.
