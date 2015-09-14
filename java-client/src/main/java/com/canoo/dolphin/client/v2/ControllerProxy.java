package com.canoo.dolphin.client.v2;

import java.util.concurrent.CompletableFuture;

/**
 * Created by hendrikebbers on 14.09.15.
 */
public interface ControllerProxy<T> {

    T getModel();

    CompletableFuture<Void> call(String actionName);

    CompletableFuture<Void> destroy();
}
