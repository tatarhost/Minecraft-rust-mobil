#!/usr/bin/env python3
"""Apply HORIZON branding to the pinned Amethyst Android launcher build."""
from pathlib import Path

path = Path("app_pojavlauncher/build.gradle")
source = path.read_text(encoding="utf-8")
replacements = {
    'applicationId "org.angelauramc.amethyst"': 'applicationId "dev.horizon.mobile.java"',
    'resValue "string", "app_name", "Amethyst (Debug)"': 'resValue "string", "app_name", "HORIZON Java"',
    'resValue "string", "app_short_name", "Amethyst (Debug)"': 'resValue "string", "app_short_name", "HORIZON"',
    "resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'": "resValue 'string', 'application_package', 'dev.horizon.mobile.java.debug'",
    "resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'": "resValue 'string', 'storageProviderAuthorities', 'dev.horizon.mobile.java.scoped.gamefolder.debug'",
    "resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'": "resValue 'string', 'shareProviderAuthority', 'dev.horizon.mobile.java.scoped.controlfolder.debug'",
}
for old, new in replacements.items():
    if old not in source:
        raise SystemExit(f"Expected branding anchor not found: {old}")
    source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print("HORIZON branding applied; Amethyst package namespace and upstream notices retained.")
