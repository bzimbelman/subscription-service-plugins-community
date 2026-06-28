// Root Gradle settings for subscription-service-plugins-community.
//
// Each plugin lives under plugins/<plugin-id>/ and is its own Gradle
// subproject. This file lists every plugin module so a single
// `./gradlew :plugins:<plugin-id>:build` invocation works from the repo
// root.
//
// Ticket #468 — slack-notifier (first community plugin).
//
// NOTE: ticket #467 lands the broader scaffold (README, CONTRIBUTING,
// CI, community-template). This file is intentionally minimal so it
// reconciles cleanly when #467 merges first.

rootProject.name = "subscription-service-plugins-community"

include("plugins:slack-notifier")
project(":plugins:slack-notifier").projectDir = file("plugins/slack-notifier")
