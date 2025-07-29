import torch
import clip

def export_clip_model_android():
    # Load the model
    device = "cpu"  # Force CPU for Android compatibility
    model, _ = clip.load("ViT-B/32", device=device)
    
    # Convert entire model to FP32 explicitly
    model = model.float()
    
    # Create dummy input in FP32
    dummy_input = torch.randn(1, 77, 512).float()  # [batch, seq_len, embed_dim]
    
    # Extract text encoder components we need
    class TextEncoderAndroid(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.token_embedding = model.token_embedding
            self.positional_embedding = model.positional_embedding
            self.transformer = model.transformer
            self.ln_final = model.ln_final
            
        def forward(self, text_tokens):
            # Embed tokens
            x = self.token_embedding(text_tokens)  # [1, 77, 512]
            x = x + self.positional_embedding
            x = x.permute(1, 0, 2)  # NLD -> LND
            x = self.transformer(x)
            x = x.permute(1, 0, 2)  # LND -> NLD
            x = self.ln_final(x)
            return x
    
    # Create and trace the wrapper
    text_encoder = TextEncoderAndroid(model).eval()
    
    # Trace with example tokenized input - keep as Long type
    example_text = clip.tokenize(["example"]).to(device)  # This will be Long type by default
    with torch.no_grad():
        traced = torch.jit.trace(text_encoder, example_text)
    
    # Save for Android
    traced.save("clip_text_encoder_android.pt")
    print("Android-compatible FP32 text encoder exported successfully!")

if __name__ == "__main__":
    export_clip_model_android()

import torch
import clip
import os

def export_clip_model():
    # Load the model
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model, preprocess = clip.load("ViT-B/32", device=device)
    
    # Create example inputs
    dummy_image = torch.randn(1, 3, 224, 224).to(device)
    dummy_text = clip.tokenize(["example text"]).to(device)
    
    # Export the image encoder
    image_encoder = model.visual
    image_encoder.eval()
    traced_image_encoder = torch.jit.trace(image_encoder, dummy_image)
    traced_image_encoder.save("clip_image_encoder.pt")

if __name__ == "__main__":
    export_clip_model() 