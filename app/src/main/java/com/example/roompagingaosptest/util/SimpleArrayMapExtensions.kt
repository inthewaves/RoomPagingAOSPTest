package com.example.roompagingaosptest.util

import androidx.collection.SimpleArrayMap

@Suppress("NOTHING_TO_INLINE")
inline operator fun <K, V> SimpleArrayMap<K, V>.set(key: K, value: V) {
    put(key, value)
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <K, V> Map<out K, V>.get(key: K): V? =
    @Suppress("UNCHECKED_CAST") (this as SimpleArrayMap<K, V>).get(key)
