import os
import json
import argparse
import numpy as np
from tensorflow import keras

MODELS_DIR = 'models'


def load_resources():
    with open(os.path.join(MODELS_DIR, 'vocab.json'), 'r', encoding='utf-8') as f:
        vocab_map = json.load(f)
    # vocab.json maps idx->char as strings
    vocab = [vocab_map[str(i)] for i in range(len(vocab_map))]
    char_to_idx = {ch: int(idx) for idx, ch in enumerate(vocab)}

    with open(os.path.join(MODELS_DIR, 'label_map.json'), 'r', encoding='utf-8') as f:
        label_map = json.load(f)

    with open(os.path.join(MODELS_DIR, 'config.json'), 'r', encoding='utf-8') as f:
        config = json.load(f)

    return char_to_idx, label_map, config


def text_to_sequence(text, char_to_idx, max_length):
    seq = [char_to_idx.get(ch, 0) for ch in text][:max_length]
    if len(seq) < max_length:
        seq += [0] * (max_length - len(seq))
    return np.array(seq, dtype=np.int32)


def load_model_prefer_keras():
    # try best_model.keras then intent_classifier.keras
    candidates = [
        os.path.join(MODELS_DIR, 'best_model.keras'),
        os.path.join(MODELS_DIR, 'intent_classifier.keras'),
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                model = keras.models.load_model(p)
                return model
            except Exception:
                continue
    raise FileNotFoundError('No Keras model found in models/.')


def predict_texts(model, texts, char_to_idx, max_length, label_map):
    X = np.array([text_to_sequence(t, char_to_idx, max_length) for t in texts])
    probs = model.predict(X)
    results = []
    for p in probs:
        idx = int(np.argmax(p))
        label = label_map.get(str(idx), str(idx))
        score = float(p[idx])
        results.append({'label': label, 'score': score, 'probs': p.tolist()})
    return results


def main():
    parser = argparse.ArgumentParser(description='Test intent classification model')
    parser.add_argument('texts', nargs='*', help='Text(s) to classify. If omitted, enters interactive mode.')
    args = parser.parse_args()

    char_to_idx, label_map, config = load_resources()
    max_length = config.get('max_length', 128)

    try:
        model = load_model_prefer_keras()
    except Exception as e:
        print('Failed to load Keras model:', e)
        return

    if args.texts:
        texts = args.texts
    else:
        try:
            inp = input('输入要分类的文本（回车结束，空行退出）: ').strip()
        except EOFError:
            return
        texts = []
        while inp:
            texts.append(inp)
            try:
                inp = input().strip()
            except EOFError:
                break

    results = predict_texts(model, texts, char_to_idx, max_length, label_map)
    for t, r in zip(texts, results):
        print('---')
        print('文本:', t)
        print('预测:', r['label'], f"(score: {r['score']:.4f})")


if __name__ == '__main__':
    main()