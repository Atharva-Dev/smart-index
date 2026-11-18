package com.kuriousdragon.smartindex.tools.seeder.role

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** SENDTO stub required for `ROLE_SMS`: toast and finish. */
class ComposeStubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "SIDX Seeder can't send messages", Toast.LENGTH_LONG).show()
        finish()
    }
}
