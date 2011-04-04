package org.springframework.data.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Try for a constructor taking state: failing that, try a no-arg
 * constructor and then setUnderlyingNode().
 * 
 * @author Rod Johnson
 */
public abstract class AbstractConstructorEntityInstantiator<BACKING_INTERFACE, STATE> implements EntityInstantiator<BACKING_INTERFACE, STATE> {

    private final Log log = LogFactory.getLog(getClass());
    private final Map<Class<? extends BACKING_INTERFACE>,StateBackedCreator<? extends BACKING_INTERFACE,STATE>> cache = new HashMap<Class<? extends BACKING_INTERFACE>,StateBackedCreator<? extends BACKING_INTERFACE,STATE>>();

	final public <T extends BACKING_INTERFACE> T createEntityFromState(STATE n, Class<T> c) {
		try {
            StateBackedCreator<T, STATE> creator = (StateBackedCreator<T, STATE>) cache.get(c);
            if (creator !=null) return creator.create(n,c);
            synchronized (cache) {
                creator = (StateBackedCreator<T, STATE>) cache.get(c);
                if (creator !=null) return creator.create(n,c);
                Class<STATE> stateClass = (Class<STATE>) n.getClass();
                creator =createInstantiator(c, stateClass);
                cache.put(c, creator);
                return creator.create(n,c);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e.getTargetException());
		}  catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}


    public void setInstantiators(Map<Class<? extends BACKING_INTERFACE>,StateBackedCreator<? extends BACKING_INTERFACE,STATE>> instantiators) {
        this.cache.putAll(instantiators);
    }

    protected <T extends BACKING_INTERFACE> StateBackedCreator<T, STATE> createInstantiator(Class<T> type, final Class<STATE> stateType) {
        StateBackedCreator<T,STATE> creator =stateTakingConstructorInstantiator(type,stateType);
        if (creator !=null) return creator;
        creator = emptyConstructorStateSettingInstantiator(type,stateType);
        if (creator !=null) return creator;
        return createFailingInstantiator(stateType);
    }

    private <T extends BACKING_INTERFACE> StateBackedCreator<T, STATE> createFailingInstantiator(final Class<STATE> stateType) {
        return new StateBackedCreator<T, STATE>() {
            public T create(STATE n, Class<T> c) throws Exception {
                throw new IllegalArgumentException(getClass().getSimpleName() + ": entity " + c +
                        " must have either a constructor taking [" + stateType + "] or a no-arg constructor and state set method");
            }
        };
    }

    private <T extends BACKING_INTERFACE> StateBackedCreator<T, STATE> emptyConstructorStateSettingInstantiator(Class<T> type, Class<STATE> stateType) {
        final Constructor<T> constructor = getNoArgConstructor(type);
        if (constructor == null) return null;

        log.info("Using " + type + " no-arg constructor");

        return new StateBackedCreator<T, STATE>() {
            public T create(STATE n, Class<T> c) throws Exception {
                try {
                    StateProvider.setUnderlyingState(n);
                    T newInstance = constructor.newInstance();
                    setState(newInstance, n);
                    return newInstance;
                } finally {
                    StateProvider.retrieveState();
                }
            }
        };
    }

    private <T extends BACKING_INTERFACE> Constructor<T> getNoArgConstructor(Class<T> type) {
        Constructor<T> constructor = ClassUtils.getConstructorIfAvailable(type);
        if (constructor != null) return constructor;
        return getDeclaredConstructor(type);
    }

    private <T extends BACKING_INTERFACE> StateBackedCreator<T, STATE> stateTakingConstructorInstantiator(Class<T> type, Class<STATE> stateType) {
        Class<? extends STATE> stateInterface = (Class<? extends STATE>) stateType.getInterfaces()[0];
        final Constructor<T> constructor = ClassUtils.getConstructorIfAvailable(type, stateInterface);
        if (constructor == null) return null;

        log.info("Using " + type + " constructor taking " + stateInterface);
        return new StateBackedCreator<T, STATE>() {
            public T create(STATE n, Class<T> c) throws Exception {
                return constructor.newInstance(n);
            }
        };
    }

    private <T> Constructor<T> getDeclaredConstructor(Class<T> c) {
        try {
            final Constructor<T> declaredConstructor = c.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            return declaredConstructor;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
	 * Subclasses must implement to set state
	 * @param entity
	 * @param s
	 */
	protected abstract void setState(BACKING_INTERFACE entity, STATE s);

}