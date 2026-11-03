package com.peykr.app

import android.app.Application
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContactRepository

interface AppContainer {
  val contactRepository: TrustedContactRepository
}

class PeykrApplication : Application(), AppContainer {
  override val contactRepository: TrustedContactRepository by lazy {
    SqliteTrustedContactRepository(this)
  }

  override fun onTerminate() {
    super.onTerminate()
    (contactRepository as? SqliteTrustedContactRepository)?.close()
  }
}
