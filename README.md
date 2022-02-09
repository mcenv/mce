# mce

[![test](https://github.com/mcenv/mce/actions/workflows/test.yml/badge.svg)](https://github.com/mcenv/mce/actions/workflows/test.yml)

A programming environment[^1] for Minecraft[^2].

## Goals

    ┌─────────────────────┐                ┌───────────────────────┐
    │ Logical consistency │                │ Output predictability │
    └─┬───────────────────┘                └─────────────────────┬─┘
      │                                                          │
      │        ┌─────────────────────┐                           │
      │        │ Static expressivity │                           │
      │        └─┬─────────────────┬─┘                           │
      │          │                 │                             │
      │          │   ┌─────────────▼───────────┐                 │
      │    ┌─────┘   │ Aggressive optimization │                 │
      │    │         └──────────┬─────┬────────┘                 │
      │    │                    │     │                          │
    ┌─▼────▼─┐ ┌────────────────▼─┐ ┌─▼─────────────┐ ┌──────────▼─┐
    │ Safety │ │ High performance │ │ Low footprint │ │ Ergonomics │
    └──────┬─┘ └────────────────┬─┘ └─┬─────────────┘ └─┬──────────┘
           │                    │     │                 │
           └──────────────────┐ │     │ ┌───────────────┘
                              │ │     │ │
                            ┌─▼─▼─────▼─▼─┐
                            │     Fun     │
                            └─────────────┘

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

## References

1. Augustsson, L. (1985). **Compiling Pattern Matching**. FPCA.
2. Le Fessant, F., & Maranget, L. (2001). **Optimizing pattern matching**. ICFP '01.
3. Levy, P.B. (2006). **Call-by-push-value: Decomposing call-by-value and call-by-name**. Higher-Order and Symbolic Computation, 19, 377-414.
4. Maranget, L. (2008). **Compiling pattern matching to good decision trees**. ML '08.
5. Yang, Y., & Oliveira, B.C. (2017). **Unifying typing and subtyping**. Proceedings of the ACM on Programming Languages, 1, 1 - 26.
6. Pédrot, P., & Tabareau, N. (2020). **The fire triangle: how to mix substitution, dependent elimination, and effects**. Proceedings of the ACM on Programming Languages, 4, 1 - 28.
7. Mokhov, A., Mitchell, N., & PEYTON JONES, S. (2020). **Build systems à la carte: Theory and practice**. Journal of Functional Programming, 30.
8. Willsey, M., Nandi, C., Wang, Y.R., Flatt, O., Tatlock, Z., & Panchekha, P. (2021). **egg: Fast and extensible equality saturation**. Proceedings of the ACM on Programming Languages, 5, 1 - 29.
9. András Kovács. (2021). [**Using Two-Level Type Theory for Staged Compilation**](https://github.com/AndrasKovacs/staged/blob/main/types2021/abstract.pdf).
10. Dunfield, J., & Krishnaswami, N.R. (2021). **Bidirectional Typing**. ACM Computing Surveys (CSUR), 54, 1 - 38.
11. Castagna, G., Laurent, M., Nguyễn, K., & Lutze, M. (2022). **On type-cases, union elimination, and occurrence typing**. Proceedings of the ACM on Programming Languages, 6, 1 - 31.
12. Xie, N., Pickering, M., Löh, A., Wu, N., Yallop, J., & Wang, M. (2022). **Staging with class: a specification for typed template Haskell**. Proceedings of the ACM on Programming Languages, 6, 1 - 30.
13. Daniel Marshall, Michael Vollmer, & Dominic Orchard. (2022). [**Linearity and Uniqueness: An Entente Cordiale**](https://starsandspira.ls/docs/esop22-draft.pdf).

[^1]: A highly integrated pair of a programming language and a development environment.
[^2]: NOT OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG.
