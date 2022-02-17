# mce

[![test](https://github.com/mcenv/mce/actions/workflows/test.yml/badge.svg)](https://github.com/mcenv/mce/actions/workflows/test.yml)

A programming environment[^1] for Minecraft[^2].

## Goals

```mermaid
flowchart TB
   LC[Logical consistency]       --> S
   SE[Static expressivity]       --> AO
   SE                          x-.-x FC
   SE                            --> S
   AO[Aggressive optimization]   --> HP
   AO                            --> LF
   AO                          x-.-x OP
   AO                          x-.-x FC
   OP[Output predictability]     --> E
   FC[Fast compilation]          --> E
   S[Safety]                     --> FP
   HP[High performance]          --> FP
   LF[Low footprint]             --> FP
   E[Ergonomics]                 --> FP[Fun programming]
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
