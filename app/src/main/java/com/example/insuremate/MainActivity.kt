package com.example.insuremate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.insuremate.ui.theme.InsureMateTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.activity.compose.BackHandler


data class PolicyData(
    val policyNumber: String,
    val policyHolder: String,
    val sumAssured: String,
    val annualPremium: String,
    val policyTerm: String,
    val expiryDate: String,
    val waitingPeriod: String,
    val deductible: String
)

class MainActivity : ComponentActivity() {

    private var cameraPermissionGranted by mutableStateOf(false)

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        setContent {
            InsureMateTheme {
                InsureMateApp(
                    cameraPermissionGranted = cameraPermissionGranted,
                    requestCameraPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}

@Composable
fun InsureMateApp(
    cameraPermissionGranted: Boolean,
    requestCameraPermission: () -> Unit
) {

    var screen by remember { mutableStateOf("home") }
    var policy by remember { mutableStateOf<PolicyData?>(null) }

    fun extractAmount(value: String): Long {
        return value
            .filter { it.isDigit() }
            .toLongOrNull() ?: 0L
    }
    BackHandler(
        enabled = screen != "home"
    ) {
        screen = "home"
    }
    when (screen) {

        "home" -> {
            HomeScreen(
                policy = policy,
                onScanClick = {
                    screen = "camera"
                },
                onCalculatorClick = {
                    screen = "calculator"
                },
                onDashboardClick = {
                    screen = "dashboard"
                }
            )
        }

        "camera" -> {
            CameraScreen(
                permissionGranted = cameraPermissionGranted,
                requestPermission = requestCameraPermission,
                onBack = {
                    screen = "home"
                },
                onTextDetected = { text ->

                    val detectedPolicy = parsePolicyText(text)

                    if (detectedPolicy != null) {
                        policy = detectedPolicy
                        screen = "dashboard"
                    }
                },
                onDemoMode = {
                    policy = demoPolicyData()
                    screen = "dashboard"
                }
            )
        }

        "dashboard" -> {
            policy?.let {
                PolicyDashboard(
                    policy = it,
                    onBack = {
                        screen = "home"
                    },
                    onCalculateCoverage = {
                        screen = "calculator"
                    },
                    onScanAnother = {
                        screen = "camera"
                    },
                    isDemoMode = false
                )
            }
        }

        "calculator" -> {
            CoverageCalculator(
                initialExistingCoverage = policy?.let {
                    extractAmount(it.sumAssured).toString()
                } ?: "",
                onBack = {
                    screen = "home"
                }
            )
        }
    }
}


/* ---------------------------------------------------------
   HOME
--------------------------------------------------------- */

@Composable
fun HomeScreen(
    policy: PolicyData?,
    onScanClick: () -> Unit,
    onCalculatorClick: () -> Unit,
    onDashboardClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        // Header

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {

                Text(
                    text = "InsureMate",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Your insurance companion",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Good morning 👋",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Text(
            text = "Protect what matters.",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(22.dp))


        // Main scan card

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202124)
            )
        ) {

            Column(
                modifier = Modifier.padding(24.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = "Understand your policy",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Scan an insurance document and turn complex policy information into simple insights.",
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {

                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Scan Policy")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))


        // Show this only after a policy has been scanned

        if (policy != null) {

            Text(
                text = "Recently Scanned Policy",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(30.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = policy.policyHolder,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = policy.policyNumber,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    Text(
                        text = "Coverage",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Text(
                        text = policy.sumAssured,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "Expiry: ${policy.expiryDate}",
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    Button(
                        onClick = onDashboardClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Policy Dashboard")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }


        // Quick tools

        Text(
            text = "Quick Tools",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onCalculatorClick()
                    },
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = "Coverage",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Calculator",
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Estimate your protection needs",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = "Smart",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Insights",
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Understand your policy better",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Demo application • Information is for demonstration purposes.",
            fontSize = 11.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@Composable
fun QuickCard(
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = Color.Gray
            )
        }
    }
}


/* ---------------------------------------------------------
   CAMERA + OCR
--------------------------------------------------------- */

@Composable
fun CameraScreen(
    permissionGranted: Boolean,
    requestPermission: () -> Unit,
    onBack: () -> Unit,
    onTextDetected: (String) -> Unit,
    onDemoMode: () -> Unit
) {

    val context = LocalContext.current

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    var scanning by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            requestPermission()
        }
    }

    if (!permissionGranted) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text("Camera permission is required.")

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = requestPermission
                ) {
                    Text("Allow Camera")
                }
            }
        }

        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            factory = { ctx ->

                val previewView = PreviewView(ctx)

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    val cameraProvider =
                        cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()

                    val capture = ImageCapture.Builder()
                        .build()

                    imageCapture = capture

                    preview.setSurfaceProvider(
                        previewView.surfaceProvider
                    )

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        context as ComponentActivity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )


        /* Top bar */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f)
            ) {

                androidx.compose.material3.IconButton(
                    onClick = onBack
                ) {

                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Scan Insurance Policy",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }


        /* Scanner frame */

        Box(
            modifier = Modifier
                .size(400.dp, 800.dp)
                .align(Alignment.Center)
                .border(
                    2.dp,
                    Color.White,
                    RoundedCornerShape(20.dp)
                )
        )


        Text(
            text = "Place the policy document inside the frame",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
        )


        Button(
            onClick = {

                if (scanning) return@Button

                scanning = true

                val capture = imageCapture ?: return@Button

                val file = File(
                    context.cacheDir,
                    "insurance_scan.jpg"
                )

                val outputOptions =
                    ImageCapture.OutputFileOptions
                        .Builder(file)
                        .build()

                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {

                        override fun onImageSaved(
                            outputFileResults: ImageCapture.OutputFileResults
                        ) {

                            try {

                                val image =
                                    InputImage.fromFilePath(
                                        context,
                                        android.net.Uri.fromFile(file)
                                    )

                                val recognizer =
                                    TextRecognition.getClient(
                                        TextRecognizerOptions.DEFAULT_OPTIONS
                                    )

                                recognizer.process(image)
                                    .addOnSuccessListener { result ->

                                        scanning = false

                                        onTextDetected(
                                            result.text
                                        )
                                    }
                                    .addOnFailureListener {

                                        scanning = false
                                    }

                            } catch (e: Exception) {

                                scanning = false
                            }
                        }

                        override fun onError(
                            exception: ImageCaptureException
                        ) {

                            scanning = false
                        }
                    }
                )

            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 55.dp)
                .size(180.dp, 55.dp)
        ) {

            if (scanning) {

                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text("Reading...")
            } else {

                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text("Scan Document")
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.65f)
        ) {
            TextButton(
                onClick = onDemoMode
            ) {
                Text(
                    text = "Use Demo Policy",
                    color = Color.White
                )
            }
        }
    }
}


/* ---------------------------------------------------------
   OCR PARSER
--------------------------------------------------------- */

fun parsePolicyText(text: String): PolicyData? {

    fun findValue(label: String): String? {

        val line = text.lines()
            .firstOrNull {
                it.contains(label, ignoreCase = true)
            }

        if (line != null) {
            val parts = line.split(":", limit = 2)

            if (parts.size == 2) {
                return parts[1].trim()
            }
        }

        return null
    }

    val policyNumber = findValue("Policy Number")
    val policyHolder = findValue("Policy Holder")
    val sumAssured = findValue("Sum Assured")
    val annualPremium = findValue("Annual Premium")
    val policyTerm = findValue("Policy Term")
    val expiryDate = findValue("Expiry Date")
    val waitingPeriod = findValue("Waiting Period")
    val deductible = findValue("Deductible")

    // Do not open the dashboard for empty or unrelated camera text
    val recognizedFields = listOf(
        policyNumber,
        policyHolder,
        sumAssured,
        annualPremium,
        policyTerm,
        expiryDate
    ).count { it != null }

    if (recognizedFields < 2) {
        return null
    }

    return PolicyData(
        policyNumber = policyNumber ?: "Not detected",
        policyHolder = policyHolder ?: "Not detected",
        sumAssured = sumAssured ?: "Not detected",
        annualPremium = annualPremium ?: "Not detected",
        policyTerm = policyTerm ?: "Not detected",
        expiryDate = expiryDate ?: "Not detected",
        waitingPeriod = waitingPeriod ?: "Not detected",
        deductible = deductible ?: "Not detected"
    )
}

/* ---------------------------------------------------------
   POLICY DASHBOARD
--------------------------------------------------------- */

@Composable
fun PolicyDashboard(
    policy: PolicyData,
    onBack: () -> Unit,
    onCalculateCoverage: () -> Unit,
    onScanAnother: () -> Unit,
    isDemoMode: Boolean
){
    var selectedTerm by remember {
        mutableStateOf<String?>(null)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(15.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            androidx.compose.material3.IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Column {

                Text(
                    text = "Your Policy",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = policy.policyNumber,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))


        /* Policy holder */

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {

            Column(
                modifier = Modifier.padding(22.dp)
            ) {

                Text(
                    text = "POLICY HOLDER",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = policy.policyHolder,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(15.dp))

        PolicyStatusCard(
            expiryDate = policy.expiryDate
        )

        Spacer(modifier = Modifier.height(15.dp))


        /* Coverage */

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202124)
            )
        ) {

            Column(
                modifier = Modifier.padding(24.dp)
            ) {

                Text(
                    text = "TOTAL COVERAGE",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = policy.sumAssured,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Protection amount",
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))


        /* Details */

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            DetailCard(
                title = "Annual Premium",
                value = policy.annualPremium,
                modifier = Modifier.weight(1f)
            )

            DetailCard(
                title = "Policy Term",
                value = policy.policyTerm,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            DetailCard(
                title = "Expiry",
                value = policy.expiryDate,
                modifier = Modifier.weight(1f)
            )

            DetailCard(
                title = "Waiting Period",
                value = policy.waitingPeriod,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Understand Your Policy",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap any term to learn what it means.",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(15.dp))

        PolicyTermCard(
            title = "Sum Assured",
            value = policy.sumAssured,
            onClick = {
                selectedTerm = "Sum Assured"
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PolicyTermCard(
            title = "Annual Premium",
            value = policy.annualPremium,
            onClick = {
                selectedTerm = "Annual Premium"
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PolicyTermCard(
            title = "Waiting Period",
            value = policy.waitingPeriod,
            onClick = {
                selectedTerm = "Waiting Period"
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PolicyTermCard(
            title = "Deductible",
            value = policy.deductible,
            onClick = {
                selectedTerm = "Deductible"
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        PolicyTermCard(
            title = "Policy Term",
            value = policy.policyTerm,
            onClick = {
                selectedTerm = "Policy Term"
            }
        )

        Spacer(modifier = Modifier.height(18.dp))


        SmartInsights(policy = policy)
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onCalculateCoverage,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "Calculate My Coverage",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onScanAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text(
                text = "Scan Another Policy",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
    if (selectedTerm != null) {

        val explanation = getPolicyTermExplanation(
            selectedTerm!!
        )

        AlertDialog(
            onDismissRequest = {
                selectedTerm = null
            },
            title = {
                Text(
                    text = selectedTerm!!
                )
            },
            text = {
                Text(
                    text = explanation
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTerm = null
                    }
                ) {
                    Text("Got it")
                }
            }
        )
    }
}
@Composable
fun PolicyStatusCard(
    expiryDate: String
) {

    val result = calculatePolicyStatus(expiryDate)

    val status = result.first
    val message = result.second
    val statusColor = result.third

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {

        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = if (status == "Active") {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Warning
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(34.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Policy Status",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Text(
                    text = status,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}
@Composable
fun PolicyTermCard(
    title: String,
    value: String,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(18.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = value,
                    color = Color.Gray
                )
            }

            Text(
                text = "Learn more ›",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
    }
}
@Composable
fun SmartInsights(
    policy: PolicyData
) {

    val score = 82

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1500),
        label = "insurance_score"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = "Smart Insights",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "A quick analysis of your policy details",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(18.dp))


        // Insurance score card

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202124)
            )
        ) {

            Column(
                modifier = Modifier.padding(24.dp)
            ) {

                Text(
                    text = "INSURANCE HEALTH SCORE",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {

                    Text(
                        text = "$animatedScore",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = " / 100",
                        color = Color.LightGray,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = {
                        animatedScore / 100f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Good protection, but some policy conditions need attention.",
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))


        // Coverage status

        InsightCard(
            title = "Coverage Status",
            description = "Your policy contains a defined sum assured of ${policy.sumAssured}. Review whether this amount is sufficient for your family's future needs.",
            icon = Icons.Default.CheckCircle,
            iconColor = Color(0xFF2E7D32)
        )

        Spacer(modifier = Modifier.height(12.dp))


        // Waiting period

        InsightCard(
            title = "Waiting Period",
            description = "Your policy has a waiting period of ${policy.waitingPeriod}. Some benefits may not be available during this period.",
            icon = Icons.Default.Warning,
            iconColor = Color(0xFFFFA000)
        )

        Spacer(modifier = Modifier.height(12.dp))


        // Deductible

        InsightCard(
            title = "Deductible",
            description = "The listed deductible is ${policy.deductible}. Check how much you may need to pay yourself before eligible benefits apply.",
            icon = Icons.Default.Warning,
            iconColor = Color(0xFFFFA000)
        )

        Spacer(modifier = Modifier.height(12.dp))


        // Recommendation

        InsightCard(
            title = "Recommended Action",
            description = "Compare your current coverage with your income, loans, dependents and long-term financial responsibilities.",
            icon = Icons.Default.Security,
            iconColor = Color(0xFF1565C0)
        )
    }
}
@Composable
fun InsightCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {

        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
@Composable
fun CoverageCalculator(
    initialExistingCoverage: String,
    onBack: () -> Unit
) {

    var annualIncome by remember {
        mutableStateOf("")
    }

    var outstandingLoan by remember {
        mutableStateOf("")
    }

    var existingCoverage by remember {
        mutableStateOf(initialExistingCoverage)
    }

    var recommendedCoverage by remember {
        mutableStateOf(0L)
    }

    var coverageGap by remember {
        mutableStateOf(0L)
    }

    var calculated by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(15.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            androidx.compose.material3.IconButton(
                onClick = onBack
            ) {

                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Coverage Calculator",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Estimate how much insurance protection you may need.",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(25.dp))


        OutlinedTextField(
            value = annualIncome,
            onValueChange = {
                annualIncome = it
                calculated = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Annual Income")
            },
            placeholder = {
                Text("Example: 1200000")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(15.dp))


        OutlinedTextField(
            value = outstandingLoan,
            onValueChange = {
                outstandingLoan = it
                calculated = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Outstanding Loan")
            },
            placeholder = {
                Text("Example: 1800000")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(15.dp))


        OutlinedTextField(
            value = existingCoverage,
            onValueChange = {
                existingCoverage = it
                calculated = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Existing Insurance Coverage")
            },
            placeholder = {
                Text("Example: 10000000")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(25.dp))


        Button(
            onClick = {

                val income =
                    annualIncome.toLongOrNull() ?: 0L

                val loan =
                    outstandingLoan.toLongOrNull() ?: 0L

                val current =
                    existingCoverage.toLongOrNull() ?: 0L

                recommendedCoverage =
                    (income * 10) + loan

                coverageGap =
                    recommendedCoverage - current

                calculated = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {

            Text(
                text = "Calculate Coverage",
                fontSize = 16.sp
            )
        }


        if (calculated) {

            Spacer(modifier = Modifier.height(25.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF202124)
                )
            ) {

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {

                    Text(
                        text = "RECOMMENDED COVERAGE",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "₹${formatIndianNumber(recommendedCoverage)}",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Estimated protection requirement",
                        color = Color.LightGray
                    )
                }
            }


            Spacer(modifier = Modifier.height(15.dp))


            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {

                Column(
                    modifier = Modifier.padding(22.dp)
                ) {

                    Text(
                        text = "Coverage Gap",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (coverageGap > 0) {

                        Text(
                            text = "₹${formatIndianNumber(coverageGap)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "Additional protection may be required.",
                            color = Color.Gray
                        )

                    } else {

                        Text(
                            text = "Your current coverage meets the estimated requirement.",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(15.dp))


            Text(
                text = "Demo calculation only — actual insurance requirements depend on individual circumstances.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
fun formatIndianNumber(number: Long): String {

    return "%,d".format(number)
}
@Composable
fun DetailCard(
    title: String,
    value: String,
    modifier: Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(
            modifier = Modifier.padding(17.dp)
        ) {

            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
fun getPolicyTermExplanation(term: String): String {

    return when (term) {

        "Sum Assured" -> {
            "Sum Assured is the amount of money the insurance policy promises " +
                    "to provide to the eligible beneficiary when a covered event occurs."
        }

        "Annual Premium" -> {
            "Annual Premium is the amount you pay to keep your insurance policy " +
                    "active for one year."
        }

        "Waiting Period" -> {
            "Waiting Period is the time you may need to wait before certain " +
                    "benefits become available under the policy. The exact rules " +
                    "depend on the insurance contract."
        }

        "Deductible" -> {
            "Deductible is the amount you may have to pay yourself before the " +
                    "insurance company pays eligible expenses. This is more common " +
                    "in health and general insurance policies."
        }

        "Policy Term" -> {
            "Policy Term is the total period for which the insurance policy " +
                    "remains active, subject to its conditions and payments."
        }

        else -> {
            "This term describes an important condition of your insurance policy. " +
                    "Check the official policy document for exact details."
        }
    }
}
fun calculatePolicyStatus(
    expiryDate: String
): Triple<String, String, Color> {

    return try {

        val formatter = DateTimeFormatter.ofPattern(
            "d MMMM yyyy",
            Locale.ENGLISH
        )

        val expiry = LocalDate.parse(
            expiryDate.trim(),
            formatter
        )

        val today = LocalDate.now()

        val daysRemaining = ChronoUnit.DAYS.between(
            today,
            expiry
        )

        when {

            daysRemaining < 0 -> {
                Triple(
                    "Expired",
                    "This policy has already expired.",
                    Color.Red
                )
            }

            daysRemaining <= 30 -> {
                Triple(
                    "Renewal Soon",
                    "$daysRemaining days remaining until expiry.",
                    Color(0xFFFFA000)
                )
            }

            else -> {
                Triple(
                    "Active",
                    "$daysRemaining days remaining until expiry.",
                    Color(0xFF2E7D32)
                )
            }
        }

    } catch (exception: Exception) {

        Triple(
            "Date Unavailable",
            "Please verify the expiry date in your policy document.",
            Color.Gray
        )
    }
}
fun demoPolicyData(): PolicyData {

    return PolicyData(
        policyNumber = "INS-2026-45821",
        policyHolder = "Rahul Sharma",
        sumAssured = "Rs. 10000000",
        annualPremium = "Rs. 24500",
        policyTerm = "20 Years",
        expiryDate = "14 August 2046",
        waitingPeriod = "2 Years",
        deductible = "Rs. 50000"
    )
}