package com.stockfortune.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.components.SfRowContainer
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.vm.SearchViewModel

@Composable
fun SearchScreen(nav: NavHostController, initialQuery: String) {
    val vm: SearchViewModel = sfViewModel { SearchViewModel.Factory(sfContainer()) }
    val st by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(initialQuery) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) vm.search(initialQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = SfColors.TextMain,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White),
            ) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.search(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SfColors.TextSub, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; vm.search("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear), tint = SfColors.TextSub)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = SfColors.TextMain,
                        unfocusedTextColor = SfColors.TextMain,
                        cursorColor = SfColors.DeepBlue,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = SfColors.TextSub,
                        unfocusedPlaceholderColor = SfColors.TextSub,
                    ),
                )
            }
        }

        when {
            st.loading -> SfLoading()
            query.isBlank() -> Text(
                text = "输入股票代码或名称开始查询",
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextSub,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            )
            st.hits.isEmpty() -> SfEmptyState(stringResource(R.string.not_found), "请检查代码是否正确")
            else -> {
                Text(
                    text = "共 ${st.hits.size} 条结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = SfColors.TextSub,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(st.hits, key = { it.stockId }) { hit ->
                        SfRowContainer(
                            onClick = { nav.navigate(Routes.stock(hit.code)) },
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(hit.symbol, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
                                    Spacer(Modifier.width(8.dp))
                                    Text(hit.name, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${hit.board} · 上市 ${hit.listingDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SfColors.TextSub,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = SfColors.OtherTag,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
