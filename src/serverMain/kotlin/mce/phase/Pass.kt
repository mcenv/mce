package mce.phase

interface Pass<I, O> {
    operator fun invoke(config: Config, input: I): O
}
