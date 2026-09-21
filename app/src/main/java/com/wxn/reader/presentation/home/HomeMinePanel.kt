package com.wxn.reader.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.reader.R
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.settings.SetListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMinePanel(innerPadding: PaddingValues, viewModel: HomeViewModel) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val isDarkTheme = when (themePreferences?.appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        else -> isSystemInDarkTheme()
    }

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopStart) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()//.padding(top = 36.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-12).dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_logo_content_desc),
                            modifier = Modifier.size(150.dp)
                        )

                    }
                }
            }

//                item {
//                    SetListItem(isDarkTheme, stringResource(R.string.deleted_books),Icons.Outlined.DeleteOutline,) {
//                        navController.navigate(Screens.DeletedBooksScreen.route)
//                    }
//                }

            item {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    SetListItem(modifier = Modifier.weight(1f).aspectRatio(1.5f), isDarkTheme,  stringResource(R.string.notes),Icons.AutoMirrored.Outlined.StickyNote2,) {
                        navController.navigate(Screens.NotesScreen.route)
                    }

                    SetListItem(modifier = Modifier.weight(1f).aspectRatio(1.5f), isDarkTheme, stringResource(R.string.statistics), Icons.Outlined.QueryStats,) {
                        navController.navigate(Screens.StatisticsScreen.route)
                    }
                }


            }

            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Spacer(Modifier.height(16.dp))
            }
            item {
                SetListItem(modifier = Modifier,
                    isDarkTheme,
                    text = stringResource(R.string.general_settings),
                    icon = Icons.Outlined.Tune) {
                    navController.navigate(Screens.GeneralSettingsScreen.route)
                }
            }

            item {
                SetListItem(modifier = Modifier,
                    isDarkTheme, stringResource(R.string.theme), Icons.Outlined.Palette) {
                    navController.navigate(Screens.ThemeScreen.route)
                }
            }

            item {
                SetListItem(modifier = Modifier,
                    isDarkTheme,
                    text = stringResource(R.string.feedback),
                    icon = Icons.Outlined.Feedback) {
                    navController.navigate(Screens.FeedbackScreen.route)
                }
            }

            item {
                SetListItem(modifier = Modifier,
                    isDarkTheme, stringResource(R.string.about),
                    Icons.Outlined.Info) {
                    navController.navigate(Screens.AboutAppScreen.route)
                }
            }
        }
    }
}
