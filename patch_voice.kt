            Spacer(modifier = Modifier.height(8.dp))
            Text("Tipo de Voz", color = TextPrimary, fontSize = 14.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModelOption("Voz 1", selectedVoice == "Voz 1") { selectedVoice = "Voz 1" }
                ModelOption("Voz 2", selectedVoice == "Voz 2") { selectedVoice = "Voz 2" }
                ModelOption("Robot", selectedVoice == "Robot") { selectedVoice = "Robot" }
            }
