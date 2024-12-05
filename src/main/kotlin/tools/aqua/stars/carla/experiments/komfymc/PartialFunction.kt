/*
 * Copyright 2024 The STARS Carla Experiments Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.stars.carla.experiments.komfymc

fun <A, B> partial(f: (A) -> B, a: A): () -> B = { f(a) }

fun <A, B, C> partial2(f: (A, B) -> C, a: A, b: B): () -> C = { f(a, b) }

fun <A, B, C> partial2First(f: (A, B) -> C, a: A): (B) -> C = { b: B -> f(a, b) }
inline fun <A, B, C> partial2FirstI(crossinline f: (A, B) -> C, a: A): (B) -> C = { b: B -> f(a, b) }

fun <A, B, C> partial2Second(f: (A, B) -> C, b: B): (A) -> C = { a: A -> f(a, b) }
inline fun <A, B, C> partial2SecondI(crossinline f: (A, B) -> C, b: B): (A) -> C = { a: A -> f(a, b) }

fun <A, B, C, D> partial3A(f: (A, B, C) -> D, a: A): (B, C) -> D = { b: B, c: C -> f(a, b, c) }

fun <A, B, C, D> partial3B(f: (A, B, C) -> D, b: B): (A, C) -> D = { a: A, c: C -> f(a, b, c) }

fun <A, B, C, D> partial3C(f: (A, B, C) -> D, c: C): (A, B) -> D = { a: A, b: B -> f(a, b, c) }

fun <A, B, C, D> partial3AB(f: (A, B, C) -> D, a: A, b: B): (C) -> D = { c: C -> f(a, b, c) }

fun <A, B, C, D> partial3BC(f: (A, B, C) -> D, b: B, c: C): (A) -> D = { a: A -> f(a, b, c) }

fun <A, B, C, D> partial3AC(f: (A, B, C) -> D, a: A, c: C): (B) -> D = { b: B -> f(a, b, c) }


inline fun <T> MutableList<T>.mutate(transform: (T) -> T): MutableList<T> {
    return mutateIndexed { _, t -> transform(t) }
}
inline fun <T> MutableList<T>.mutateIndexed(transform: (Int, T) -> T): MutableList<T> {
    val iterator = listIterator()
    var i = 0
    while (iterator.hasNext()) {
        iterator.set(transform(i++, iterator.next()))
    }
    return this
}