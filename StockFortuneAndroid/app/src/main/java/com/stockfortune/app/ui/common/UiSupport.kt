package com.stockfortune.app.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.stockfortune.app.AppContainer
import com.stockfortune.app.AppRuntime

/** 非 Composable 取容器：便于在 ViewModel 工厂 lambda 中调用。 */
fun sfContainer(): AppContainer = AppRuntime.container

/** 统一构造 ViewModel：`val vm: HomeViewModel = sfViewModel { HomeViewModel.Factory(sfContainer()) }` */
@Composable
inline fun <reified VM : ViewModel> sfViewModel(noinline factory: () -> ViewModelProvider.Factory): VM {
    val owner = LocalViewModelStoreOwner.current ?: error("缺少 ViewModelStoreOwner")
    return ViewModelProvider(owner.viewModelStore, factory())[VM::class.java]
}

fun <VM : ViewModel> vmFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

