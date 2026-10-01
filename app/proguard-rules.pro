# R8 rules for :app's release build (LLM.md §10). Empty on purpose: every library here ships its own consumer
# rules — kotlinx.serialization keeps the @Serializable nav routes' serializers, Media3, Koin's DSL and Compose
# need nothing more — and the app reads its JSON as a JsonElement tree, with no reflection.
# Add a rule only with the crash it fixes, so nobody has to guess later whether it can go.
