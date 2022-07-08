package mce.pass

interface Pass<I, O> {
    operator fun invoke(config: Config, input: I): O
}
