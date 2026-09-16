package com.penink.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.penink.vpn.ui.main.MainScreen
import com.penink.vpn.ui.nodes.NodeEditScreen
import com.penink.vpn.ui.nodes.NodeListScreen
import com.penink.vpn.ui.nodes.NodeViewModel

@Composable
fun AppNavHost(viewModel: NodeViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onOpenNodes = { navController.navigate("nodes") }
            )
        }

        composable("nodes") {
            NodeListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddNode = { navController.navigate("node_edit/-1") },
                onEditNode = { node -> navController.navigate("node_edit/${node.id}") }
            )
        }

        composable(
            route = "node_edit/{nodeId}",
            arguments = listOf(
                navArgument("nodeId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            NodeEditScreen(
                viewModel = viewModel,
                nodeId = entry.arguments?.getLong("nodeId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}