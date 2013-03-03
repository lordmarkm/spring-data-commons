/*
 * Copyright 2013 the original author or authors.
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
package org.springframework.data.repository.augment;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.data.repository.augment.QueryContext.QueryMode;
import org.springframework.data.repository.core.EntityMetadata;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Wrapper for a collection of {@link QueryAugmentor}s. Groups them by the context type they're referring to for the
 * appropriate invocation later on.
 * 
 * @since 1.6
 * @author Oliver Gierke
 */
public class QueryAugmentationEngine {

	private static final Iterable<QueryAugmentor<? extends QueryContext<?>, ? extends UpdateContext<?>>> NO_AUGMENTORS = Collections
			.emptySet();
	public static final QueryAugmentationEngine NONE = new QueryAugmentationEngine(NO_AUGMENTORS, null, false);

	private static final Logger LOGGER = LoggerFactory.getLogger(QueryAugmentationEngine.class);
	private static final Comparator<Object> COMPARATOR = new AnnotationAwareOrderComparator();

	private final MultiValueMap<Class<?>, QueryAugmentor<QueryContext<?>, UpdateContext<?>>> augmentors = new LinkedMultiValueMap<Class<?>, QueryAugmentor<QueryContext<?>, UpdateContext<?>>>();
	private final MethodMetadata methodMetadata;

	/**
	 * Creates a new {@link QueryAugmentationEngine} by inspecting the given {@link QueryAugmentor}s.
	 * 
	 * @param augmentors must not be {@literal null}.
	 * @param metadataProvider must not be {@literal null}.
	 */
	public QueryAugmentationEngine(
			Iterable<QueryAugmentor<? extends QueryContext<?>, ? extends UpdateContext<?>>> augmentors,
			MethodMetadata metadataProvider) {
		this(augmentors, metadataProvider, true);
	}

	/**
	 * Internal constructor to allow {@link #NONE} being created with a {@literal null} {@link MethodMetadata} which
	 * actually must not be null otherwise.
	 * 
	 * @param augmentors the {@link QueryAugmentor}s to register.
	 * @param methodMetadata
	 * @param checkNull whether to check the {@link MethodMetadata} for {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	private QueryAugmentationEngine(
			Iterable<QueryAugmentor<? extends QueryContext<?>, ? extends UpdateContext<?>>> augmentors,
			MethodMetadata methodMetadata, boolean checkNull) {

		Assert.notNull(augmentors, "QueryAugmentors must not be null!");

		if (checkNull) {
			Assert.notNull(methodMetadata, "MethodMetadata must not be null!");
		}

		this.methodMetadata = methodMetadata;

		for (QueryAugmentor<? extends QueryContext<?>, ? extends UpdateContext<?>> augmentor : augmentors) {
			Class<?>[] keys = GenericTypeResolver.resolveTypeArguments(augmentor.getClass(), QueryAugmentor.class);
			QueryAugmentor<QueryContext<?>, UpdateContext<?>> castedAugmentor = (QueryAugmentor<QueryContext<?>, UpdateContext<?>>) augmentor;
			this.augmentors.add(keys[0], castedAugmentor);
			this.augmentors.add(keys[1], castedAugmentor);
		}

		for (List<QueryAugmentor<QueryContext<?>, UpdateContext<?>>> values : this.augmentors.values()) {
			Collections.sort(values, COMPARATOR);
		}
	}

	/**
	 * Returns whether there's any {@link QueryAugmentor} registered to be invoked for the given context.
	 * 
	 * @param contextType the context type about to be handled.
	 * @param queryMode the execution mode.
	 * @param metadata the {@link EntityMetadata}.
	 * @return
	 */
	public boolean augmentationNeeded(Class<?> contextType, QueryMode queryMode, EntityMetadata<?> metadata) {

		if (!augmentors.containsKey(contextType)) {
			return false;
		}

		for (QueryAugmentor<QueryContext<?>, UpdateContext<?>> augmentor : augmentors.get(contextType)) {
			if (augmentor.supports(methodMetadata, queryMode, metadata)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Invokes all {@link QueryAugmentor}s registered for the given {@link QueryContext}.
	 * 
	 * @param context
	 */
	@SuppressWarnings("unchecked")
	public <T extends QueryContext<?>> T invokeAugmentors(T context) {

		T augmentedContext = context;

		for (QueryAugmentor<QueryContext<?>, UpdateContext<?>> augmentor : augmentors.get(context.getClass())) {

			LOGGER.debug("Invoking query augmentor {} for query context {}", augmentor, context);
			augmentedContext = (T) augmentor.augmentQuery(augmentedContext, methodMetadata);
		}

		return augmentedContext;
	}

	/**
	 * Invokes the registered {@link QueryAugmentor}s using the given {@link UpdateContext}.
	 * 
	 * @param context must not be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends UpdateContext<?>> T invokeAugmentors(T context) {

		Assert.notNull(context, "UpdateContext must not be null!");
		T augmentedContext = context;

		for (QueryAugmentor<QueryContext<?>, UpdateContext<?>> augmentor : augmentors.get(context.getClass())) {

			LOGGER.debug("Invoking query augmentor {} for query context {}", augmentor, context);
			augmentedContext = (T) augmentor.augmentUpdate(augmentedContext, methodMetadata);

			if (augmentedContext == null) {
				return null;
			}
		}

		return augmentedContext;
	}
}
