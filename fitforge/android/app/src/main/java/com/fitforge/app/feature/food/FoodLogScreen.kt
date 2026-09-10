@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.fitforge.app.feature.food

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitforge.app.R
import com.fitforge.app.core.database.FoodItemEntity
import com.fitforge.app.data.repository.NutritionPreview
import java.io.ByteArrayOutputStream

@Composable
fun FoodLogScreen(
    onDone: () -> Unit,
    viewModel: FoodLogViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
            viewModel.labelPhoto(bytes)
        } else {
            viewModel.reset()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraLauncher.launch(null) else viewModel.reset()
    }
    val capturePhoto = {
        viewModel.beginPhoto()
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.food_log_title), style = MaterialTheme.typography.headlineSmall)
        when (val current = step) {
            FoodLogStep.ChooseMethod -> ChooseMethod(
                onPhoto = capturePhoto,
                onSearch = viewModel::beginSearch,
                onBarcode = viewModel::beginBarcode,
                onBack = onDone,
            )
            is FoodLogStep.Label -> LabelStep(current, viewModel::selectLabel, viewModel::beginSearch, onDone)
            is FoodLogStep.Search -> SearchStep(
                current,
                query,
                results,
                viewModel::updateSearch,
                viewModel::selectSearchResult,
                onDone,
            )
            is FoodLogStep.Barcode -> BarcodeStep(current, viewModel::findBarcode, onDone)
            is FoodLogStep.Portion -> PortionStep(
                current,
                viewModel::updatePortion,
                viewModel::confirmPortion,
                onDone,
            )
            is FoodLogStep.Nutrition -> NutritionStep(current.preview, viewModel::logNutrition, onDone)
            FoodLogStep.Logged -> {
                Text(stringResource(R.string.food_logged))
                Button(onClick = onDone) { Text(stringResource(R.string.food_done)) }
            }
        }
    }
}

@Composable
private fun ChooseMethod(onPhoto: () -> Unit, onSearch: () -> Unit, onBarcode: () -> Unit, onBack: () -> Unit) {
    Button(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_photo))
    }
    OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_search))
    }
    OutlinedButton(onClick = onBarcode, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_barcode))
    }
    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.food_cancel)) }
}

@Composable
private fun LabelStep(
    step: FoodLogStep.Label,
    onSelect: (FoodItemEntity) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    StepTitle(1, stringResource(R.string.food_step_label))
    when {
        step.loading -> CircularProgressIndicator()
        step.error != null -> {
            val errorText = if (step.error == FoodLabelError.NOT_CONFIGURED) {
                R.string.food_gemini_not_configured
            } else {
                R.string.food_recognition_unavailable
            }
            Text(stringResource(errorText), color = MaterialTheme.colorScheme.error)
            Button(onClick = onSearch) { Text(stringResource(R.string.food_search_instead)) }
        }
        else -> {
            Text(stringResource(R.string.food_choose_label))
            step.candidates.forEach { food -> FoodRow(food) { onSelect(food) } }
            OutlinedButton(onClick = onSearch) { Text(stringResource(R.string.food_none_of_these)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.food_cancel)) }
        }
    }
}

@Composable
private fun SearchStep(
    step: FoodLogStep.Search,
    query: String,
    results: List<FoodItemEntity>,
    onQuery: (String) -> Unit,
    onSelect: (FoodItemEntity) -> Unit,
    onCancel: () -> Unit,
) {
    StepTitle(2, stringResource(R.string.food_step_portion))
    // Placed before the results list on purpose: a LazyColumn with no weight
    // consumes the rest of the column's height, so anything after it in this
    // unscrolled Column would render off-screen.
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.food_cancel)) }
    if (step.fallbackFromPhoto) Text(stringResource(R.string.food_label_fallback))
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        label = { Text(stringResource(R.string.food_search_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn { items(results, key = { it.slug }) { FoodRow(it) { onSelect(it) } } }
}

@Composable
private fun BarcodeStep(step: FoodLogStep.Barcode, onLookup: (String) -> Unit, onCancel: () -> Unit) {
    var barcode by remember { mutableStateOf("") }
    StepTitle(2, stringResource(R.string.food_step_portion))
    Text(stringResource(R.string.food_barcode_intro))
    OutlinedTextField(
        value = barcode,
        onValueChange = { barcode = it.filter(Char::isDigit) },
        label = { Text(stringResource(R.string.food_barcode_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (step.notFound) {
        Text(stringResource(R.string.food_barcode_not_found), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = { onLookup(barcode) }, enabled = barcode.isNotBlank()) {
        Text(stringResource(R.string.food_find_barcode))
    }
    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.food_cancel)) }
}

@Composable
private fun PortionStep(
    step: FoodLogStep.Portion,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    StepTitle(2, stringResource(R.string.food_step_portion))
    Text(step.food.name, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.food_portion_default, step.food.portionLabel))
    OutlinedTextField(
        value = step.grams,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.food_portion_grams)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    if (step.invalid) {
        Text(stringResource(R.string.food_invalid_portion), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_confirm_portion))
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_cancel))
    }
}

@Composable
private fun NutritionStep(preview: NutritionPreview, onLog: () -> Unit, onCancel: () -> Unit) {
    StepTitle(3, stringResource(R.string.food_step_nutrition))
    Text(preview.food.name, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.food_nutrition_portion, preview.portionG))
    Text(stringResource(R.string.food_calories, preview.calories))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.food_protein, preview.proteinG))
        Text(stringResource(R.string.food_carbs, preview.carbsG))
        Text(stringResource(R.string.food_fat, preview.fatG))
    }
    Text(stringResource(R.string.food_nutrition_source, preview.food.source))
    Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_log_confirm))
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.food_cancel))
    }
}

@Composable
private fun StepTitle(number: Int, title: String) {
    Text(stringResource(R.string.food_step_title, number, title), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun FoodRow(food: FoodItemEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            Text(food.portionLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}
