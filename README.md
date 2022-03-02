# mce

[![test](https://github.com/mcenv/mce/actions/workflows/test.yml/badge.svg)](https://github.com/mcenv/mce/actions/workflows/test.yml)

A programming environment[^1] for Minecraft[^2].

## Goals

```mermaid
flowchart TB
  DT[Dependnet types]         --->   SE
  MT[Multiphase types]        --->   SE
  SU[Subtyping]               --->   SE
  OT[Occurrence typing]       --->   SE
  AE[Algebraic effects]       --->   SE
  CA[Cost analysis]           --->   SE
  SE[Static expressivity]     --->   AO
  SE[Static expressivity]     --->   SA
  LC[Logical consistency]     --->   SA
  ST[Staging]                 --->   HP
  ST[Staging]                 --->   PO
  PO[Predictable output]      --->   ER
  AO[Aggressive optimization] --->   LF
  AO[Aggressive optimization] --->   HP
  PE[Projectional editing]    --->   IC
  PE[Projectional editing]    --->   ER
  IC[Incremental compilation] --->   FC 
  PC[Parallel compilation]    --->   FC 
  FC[Fast compilation]        --->   ER
  SA[Safety]                  -----> ER
  LF[Low footprint]           --->   PEFM
  HP[High performance]        --->   PEFM
  ER[Ergonomics]              --->   PEFM[Programming environment for Minecraft]
```

[^1]: A highly integrated pair of a programming language and a development environment.
[^2]: NOT OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG.
