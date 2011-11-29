/*
 * Copyright 2006-2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.cloudfoundry.identity.uaa.config;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServletResponse;

import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;

/**
 * Factory for a handler adapter that sniffs the results from {@link RequestMapping} method executions and adds an ETag
 * header if the result is a {@link ScimUser}. Inject into application context as anonymous bean.
 * 
 * @author Dave Syer
 * 
 */
public class HandlerAdapterFactoryBean implements FactoryBean<HandlerAdapter> {

	@Override
	public HandlerAdapter getObject() throws Exception {
		AnnotationMethodHandlerAdapter adapter = new AnnotationMethodHandlerAdapter();
		adapter.setMessageConverters(getMessageConverters());
		adapter.setOrder(0);
		adapter.setCustomModelAndViewResolver(new ScimEtagModelAndViewResolver());
		return adapter;
	}

	private HttpMessageConverter<?>[] getMessageConverters() {
		return new RestTemplate().getMessageConverters().toArray(new HttpMessageConverter[0]);
	}

	@Override
	public Class<?> getObjectType() {
		return AnnotationMethodHandlerAdapter.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	private static class ScimEtagModelAndViewResolver implements ModelAndViewResolver {

		@Override
		public ModelAndView resolveModelAndView(Method handlerMethod, @SuppressWarnings("rawtypes") Class handlerType,
				Object returnValue, ExtendedModelMap implicitModel, NativeWebRequest webRequest) {
			if (returnValue instanceof ScimUser) {
				HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
				response.addHeader("ETag", "" + ((ScimUser) returnValue).getVersion());
			}
			return ModelAndViewResolver.UNRESOLVED;
		}

	}
}
