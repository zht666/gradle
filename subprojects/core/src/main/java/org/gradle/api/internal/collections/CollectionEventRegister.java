/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.collections;

import org.gradle.api.Action;

import javax.annotation.Nullable;

public interface CollectionEventRegister<T> {
    boolean isSubscribed(@Nullable Class<?> type);

    Action<T> getAddAction();

    Action<T> getRemoveAction();

    void registerEagerAddAction(Class<? extends T> type, Action<? super T> addAction);

    void registerLazyAddAction(Action<? super T> addAction);

    void registerRemoveAction(Class<? extends T> type, Action<? super T> removeAction);
}
