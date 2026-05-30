import logging
import os
import json
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from sklearn.preprocessing import LabelEncoder
from sklearn.model_selection import train_test_split
from sklearn.utils.class_weight import compute_class_weight
import random

# 检查 TensorFlow 版本（应该是 2.11.0）
print(f"TensorFlow version: {tf.__version__}")

logging.basicConfig(level=logging.INFO, format='%(message)s')
log = logging.getLogger('train_model')

MODELS_DIR = 'models'
os.makedirs(MODELS_DIR, exist_ok=True)


def load_data(path='training_data.csv'):
    df = pd.read_csv(path, encoding='utf-8')
    texts = df['文本'].astype(str).values
    labels = df['意图'].astype(str).values
    return texts, labels


def build_vocab(texts):
    vocab = set()
    for t in texts:
        for ch in t:
            vocab.add(ch)
    vocab = sorted(list(vocab))
    vocab = ['<PAD>'] + vocab
    return vocab


def text_to_sequence(text, char_to_idx, max_length):
    seq = [char_to_idx.get(ch, 0) for ch in text][:max_length]
    if len(seq) < max_length:
        seq += [0] * (max_length - len(seq))
    return np.array(seq, dtype=np.int32)


def main():
    # for reproducibility
    seed = 42
    np.random.seed(seed)
    random.seed(seed)
    tf.random.set_seed(seed)

    log.info('Loading training data...')
    texts, labels = load_data()
    log.info(f'Samples: {len(texts)}')

    # labels
    le = LabelEncoder()
    y = le.fit_transform(labels)
    num_classes = len(le.classes_)
    label_map = {str(i): label for i, label in enumerate(le.classes_)}
    with open(os.path.join(MODELS_DIR, 'label_map.json'), 'w', encoding='utf-8') as f:
        json.dump(label_map, f, ensure_ascii=False, indent=2)

    # vocab
    vocab = build_vocab(texts)
    char_to_idx = {ch: i for i, ch in enumerate(vocab)}
    vocab_size = len(vocab)
    max_length = 128
    log.info(f'Vocab size: {vocab_size}, max_length: {max_length}')

    X = np.array([text_to_sequence(t, char_to_idx, max_length) for t in texts])

    # split
    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.15, random_state=42, stratify=y)
    log.info(f'Train samples: {len(X_train)}, Val samples: {len(X_val)}')

    # class weights
    class_weights = None
    try:
        classes = np.unique(y_train)
        weights = compute_class_weight('balanced', classes=classes, y=y_train)
        class_weights = {int(c): float(w) for c, w in zip(classes, weights)}
        log.info('Computed class weights.')
    except Exception:
        log.info('Could not compute class weights; proceeding without them.')

    # model - Conv1D + GlobalMaxPool for short character sequences
    embedding_dim = 128
    inputs = keras.Input(shape=(max_length,), dtype='int32', name='input_layer')
    x = layers.Embedding(vocab_size, embedding_dim, mask_zero=False)(inputs)
    x = layers.Conv1D(128, 3, padding='same', activation='relu')(x)
    x = layers.Conv1D(128, 5, padding='same', activation='relu')(x)
    x = layers.GlobalMaxPooling1D()(x)
    x = layers.Dense(256, activation='relu')(x)
    x = layers.Dropout(0.3)(x)
    x = layers.Dense(128, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(num_classes, activation='softmax')(x)
    model = keras.Model(inputs=inputs, outputs=outputs)
    model.compile(optimizer=keras.optimizers.Adam(learning_rate=1e-3),
                  loss='sparse_categorical_crossentropy',
                  metrics=['accuracy'])

    # callbacks
    best_path = os.path.join(MODELS_DIR, 'best_model.keras')
    callbacks = [
        keras.callbacks.EarlyStopping(monitor='val_loss', patience=6, restore_best_weights=True, verbose=0),
        keras.callbacks.ReduceLROnPlateau(monitor='val_loss', factor=0.5, patience=3, verbose=0),
        keras.callbacks.ModelCheckpoint(best_path, save_best_only=True, verbose=0)
    ]

    log.info('Training model...')
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=50,
        batch_size=16,
        class_weight=class_weights,
        callbacks=callbacks,
        verbose=2
        ,
        shuffle=True
    )

    # load best model (if saved)
    try:
        best_model = keras.models.load_model(best_path)
        model = best_model
    except Exception:
        pass

    # evaluate
    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    log.info(f'Validation - loss: {val_loss:.4f}, acc: {val_acc:.4f}')

    # export to TFLite
    log.info('Converting to TFLite with maximum compatibility...')
    try:
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        
        # 包含 SELECT_TF_OPS 以支持 TensorFlow 运算
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS
        ]
        
        # 保持默认精度和优化
        converter.target_spec.supported_types = [tf.float32]
        converter.optimizations = []
        
        # 不显式切换转换器，让系统自动选择最新稳定版
        
        tflite_model = converter.convert()
        tflite_path = os.path.join(MODELS_DIR, 'intent_classifier.tflite')
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)
        log.info(f'TFLite saved: {tflite_path} ({len(tflite_model)/1024:.2f} KB)')
        log.info('✓ TFLite conversion successful - compatible with TFLite 2.14+')
    except Exception as e:
        log.warning('TFLite conversion failed; saved Keras model instead.')
        model.save(os.path.join(MODELS_DIR, 'intent_classifier.keras'), include_optimizer=False)
        log.warning(f'Error: {str(e)}')

    # save vocab and config
    vocab_map = {str(idx): ch for idx, ch in enumerate(vocab)}
    with open(os.path.join(MODELS_DIR, 'vocab.json'), 'w', encoding='utf-8') as f:
        json.dump(vocab_map, f, ensure_ascii=False, indent=2)

    config = {
        'vocab_size': vocab_size,
        'max_length': max_length,
        'num_classes': num_classes,
        'embedding_dim': embedding_dim,
        'model_type': 'embedding_dense'
    }
    with open(os.path.join(MODELS_DIR, 'config.json'), 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=2)

    log.info('Training and export complete. Artifacts in "models/"')


if __name__ == '__main__':
    main()