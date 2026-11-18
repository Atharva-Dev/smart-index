package com.kuriousdragon.smartindex.sync.fakes

import com.kuriousdragon.smartindex.sync.FreeSpace

class FakeFreeSpace(var bytes: Long = Long.MAX_VALUE) : FreeSpace {
    override fun availableBytes(): Long = bytes
}
