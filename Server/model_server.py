import tensorflow as tf
from flask import Flask, request, jsonify
import numpy as np
import json

app = Flask(__name__)

# --- FINAL MODEL LOADING PROCESS ---
print("Python: Loading the NUMBERS-ONLY Keras model...")
model = tf.keras.models.load_model('fraud_model.h5', compile=False)
print("Python: Model loaded successfully.")

print("Python: Loading vocabulary from JSON...")
with open('vocabulary.json', 'r') as f:
    vocab_list = json.load(f)
# Create a dictionary for fast lookups
vocab_dict = {word: index for index, word in enumerate(vocab_list)}
print("Python: Vocabulary loaded successfully.")

SEQUENCE_LENGTH = 100 # Must match Colab
# --- END LOADING ---

# This function does the text-to-number conversion manually
def preprocess_text(text):
    tokens = text.lower().split()
    sequence = [vocab_dict.get(word, 1) for word in tokens] # Use 1 (OOV token) for unknown words
    padded_sequence = tf.keras.preprocessing.sequence.pad_sequences([sequence], maxlen=SEQUENCE_LENGTH, padding='post', truncating='post')
    return padded_sequence

@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json(force=True)
        text_to_predict = data['text']
        
        # 1. Manually convert text to a padded array of numbers
        vectorized_text = preprocess_text(text_to_predict)
        
        # 2. Predict using the numbers-only model
        prediction = model.predict(vectorized_text)
        
        score = float(prediction[0][0])
        print(f"Python: Received text '{text_to_predict}', predicted score: {score}")
        
        return jsonify({'score': score})
    except Exception as e:
        print(f"Python: Error during prediction: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)