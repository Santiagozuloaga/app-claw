    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sendChatMessage("Archivo adjunto: ${uri.lastPathSegment}", attachedFile = uri.toString())
        }
    }
