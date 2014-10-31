/**
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

package com.lyncode.jtwig.mvc;

import com.lyncode.jtwig.configuration.JtwigConfiguration;
import com.lyncode.jtwig.functions.SpringFunctions;
import com.lyncode.jtwig.functions.parameters.convert.DemultiplexerConverter;
import com.lyncode.jtwig.functions.parameters.convert.impl.ObjectToStringConverter;
import com.lyncode.jtwig.functions.parameters.input.InputParameters;
import com.lyncode.jtwig.functions.parameters.resolve.HttpRequestParameterResolver;
import com.lyncode.jtwig.functions.parameters.resolve.api.InputParameterResolverFactory;
import com.lyncode.jtwig.functions.parameters.resolve.api.ParameterResolver;
import com.lyncode.jtwig.functions.parameters.resolve.impl.CompoundParameterResolver;
import com.lyncode.jtwig.functions.parameters.resolve.impl.InputDelegateMethodParametersResolver;
import com.lyncode.jtwig.functions.parameters.resolve.impl.ParameterAnnotationParameterResolver;
import com.lyncode.jtwig.functions.resolver.api.FunctionResolver;
import com.lyncode.jtwig.functions.resolver.impl.CompoundFunctionResolver;
import com.lyncode.jtwig.functions.resolver.impl.DelegateFunctionResolver;
import com.lyncode.jtwig.services.api.url.ResourceUrlResolver;
import com.lyncode.jtwig.services.impl.url.IdentityUrlResolver;
import com.lyncode.jtwig.services.impl.url.ThemedResourceUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ThemeResolver;
import org.springframework.web.servlet.view.AbstractTemplateViewResolver;
import org.springframework.web.servlet.view.AbstractUrlBasedView;

import static com.lyncode.jtwig.render.stream.RenderStream.withMaxThreads;
import static com.lyncode.jtwig.render.stream.RenderStream.withMinThreads;
import static com.lyncode.jtwig.util.LocalThreadHolder.getServletRequest;

@Service
public class JtwigViewResolver extends AbstractTemplateViewResolver {
    @Autowired(required = false)
    private ThemeResolver themeResolver;

    private String encoding;
    private boolean cached = true;
    private boolean useThemeInViewPath = false;

    private JtwigConfiguration configuration = new JtwigConfiguration();
    private SpringFunctions springFunctions = null;
    private CompoundParameterResolver parameterResolver = new CompoundParameterResolver();
    private CompoundFunctionResolver functionResolver = new CompoundFunctionResolver()
            .withResolver(resolver(new DemultiplexerConverter()))
            .withResolver(resolver(new DemultiplexerConverter().withConverter(String.class, new ObjectToStringConverter())));

    public JtwigViewResolver() {
        setViewClass(requiredViewClass());
        setContentType("text/html; charset=UTF-8");

        parameterResolver
                .withResolver(new HttpRequestParameterResolver());
    }

    @Override
    protected Class<?> requiredViewClass() {
        return JtwigView.class;
    }

    @Override
    protected AbstractUrlBasedView buildView(String viewName) throws Exception {
        AbstractUrlBasedView abstractUrlBasedView = super.buildView(viewName);
        abstractUrlBasedView.setUrl(urlResolver().resolve(getPrefix(), viewName, getSuffix()));
        return abstractUrlBasedView;
    }

    private ResourceUrlResolver urlResolver() {
        if (themeResolver == null || !useThemeInViewPath)
            return IdentityUrlResolver.INSTANCE;
        else
            return new ThemedResourceUrlResolver(themeResolver.resolveThemeName(getServletRequest()));
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public void setThemeResolver(ThemeResolver themeResolver) {
        this.themeResolver = themeResolver;
    }

    public boolean useThemeInViewPath() {
        return useThemeInViewPath;
    }

    public void setUseThemeInViewPath(boolean useThemeInViewPath) {
        this.useThemeInViewPath = useThemeInViewPath;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setConcurrentMaxThreads (int value) {
        withMaxThreads(value);
    }

    public void setConcurrentMinThreads (int value) {
        withMinThreads(value);
    }

    public FunctionResolver functionResolver() {
        if (springFunctions == null) {
            springFunctions = new SpringFunctions();
            getApplicationContext().getAutowireCapableBeanFactory().autowireBean(springFunctions);
            configuration.render().functionRepository().include(springFunctions);
        }
        return functionResolver;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public JtwigConfiguration configuration() {
        return configuration;
    }

    public void setConfiguration (JtwigConfiguration configuration) {
        this.configuration = configuration;
    }

    public JtwigViewResolver include (ParameterResolver resolver) {
        parameterResolver.withResolver(resolver);
        return this;
    }

    public JtwigViewResolver includeFunctions (Object functionBean) {
        configuration.render().functionRepository().include(functionBean);
        return this;
    }

    private InputParameterResolverFactory parameterResolverFactory(final DemultiplexerConverter converter) {
        return new InputParameterResolverFactory() {
            @Override
            public ParameterResolver create(InputParameters parameters) {
                return new CompoundParameterResolver()
                        .withResolver(new ParameterAnnotationParameterResolver(parameters, converter))
                        .withResolver(parameterResolver);
            }
        };
    }

    private DelegateFunctionResolver resolver(DemultiplexerConverter converter) {
        return new DelegateFunctionResolver(configuration.render().functionRepository(), new InputDelegateMethodParametersResolver(parameterResolverFactory(converter)));
    }
}
