/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.server.internal.inject;

import javax.ws.rs.BeanParam;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.spi.inject.Descriptors;
import org.glassfish.jersey.spi.inject.ForeignDescriptor;
import org.glassfish.jersey.spi.inject.InstanceManager;

import org.glassfish.hk2.utilities.cache.Cache;
import org.glassfish.hk2.utilities.cache.Computable;

/**
 * Value factory provider for {@link BeanParam bean parameters}.
 *
 * @author Miroslav Fuksa
 */
@Singleton
final class BeanParamValueSupplierProvider extends AbstractValueSupplierProvider {

    private final InstanceManager instanceManager;

    private static final class BeanParamValueSupplier extends AbstractRequestDerivedValueSupplier<Object> {
        private final Parameter parameter;
        private final InstanceManager instanceManager;

        private final Cache<Class<?>, ForeignDescriptor> descriptorCache
                = new Cache<>(new Computable<Class<?>, ForeignDescriptor>() {

                    @Override
                    public ForeignDescriptor compute(Class<?> key) {
                        // below we make sure HK2 behaves as if injection happens into a request scoped type
                        // this is to avoid having proxies injected (see JERSEY-2386)
                        // before touching the following statement, check BeanParamMemoryLeakTest first!
                        return instanceManager
                                .createForeignDescriptor(Descriptors.serviceAsContract(key).in(RequestScoped.class));
                    }
                });

        private BeanParamValueSupplier(InstanceManager instanceManager, Parameter parameter,
                Provider<ContainerRequest> requestProvider) {
            super(requestProvider);

            this.instanceManager = instanceManager;
            this.parameter = parameter;
        }

        @Override
        public Object get() {
            Class<?> rawType = parameter.getRawType();
            Object fromHk2 = instanceManager.getInstance(rawType);
            if (fromHk2 != null) { // the bean parameter type is already bound in HK2, let's just take it from there
                return fromHk2;
            }
            ForeignDescriptor foreignDescriptor = descriptorCache.compute(rawType);
            return instanceManager.getInstance(foreignDescriptor);
        }
    }

    /**
     * Creates new instance initialized from parameters injected by HK2.
     *
     * @param mpep            multivalued parameter extractor provider.
     * @param requestProvider request provider.
     */
    public BeanParamValueSupplierProvider(MultivaluedParameterExtractorProvider mpep,
            Provider<ContainerRequest> requestProvider, InstanceManager instanceManager) {
        super(mpep, requestProvider, Parameter.Source.BEAN_PARAM);
        this.instanceManager = instanceManager;
    }

    @Override
    public AbstractRequestDerivedValueSupplier<?> createValueSupplier(
            Parameter parameter,
            Provider<ContainerRequest> requestProvider) {

        return new BeanParamValueSupplier(instanceManager, parameter, requestProvider);
    }
}
