rootProject.name = "intellij-rust"
// Special module with run, build and publish tasks
include("plugin")
include(
    "common",
    "idea",
    "clion",
    "debugger",
    "toml",
    "copyright",
    "coverage",
    "intelliLang",
    "duplicates",
    "grazie",
    "js",
    "ml-completion",
    "wsl",
    "intellij-toml"
)
