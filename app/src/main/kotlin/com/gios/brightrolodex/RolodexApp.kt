package com.gios.brightrolodex

import android.app.Application

/**
 * Nothing to install.
 *
 * The other Bright* apps register light-common's crash reporter here. This one does not depend on
 * light-common at all — see `settings.gradle.kts` — so there is no reporter yet and no network
 * permission for one to use. The class exists so that adding one later is a change in one file
 * rather than a manifest edit plus a new class.
 */
class RolodexApp : Application()
