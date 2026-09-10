"""A small 1D-CNN over a sliding window of per-frame joint-landmark features —
a sequence model over pose landmarks, not a video/raw-frame classifier, per
the pose-based exercise-recognition literature this follows. The same shape
serves both classification tasks (exercise type, rep quality); only
`num_classes` and the training data differ.

Depth/width are a reasonable small-model starting point, not a tuned result —
real hyperparameter search needs real data (see README.md's sample-size gate).
"""

import tensorflow as tf
from tensorflow import keras


def build_sequence_classifier(window_length: int, num_features: int, num_classes: int) -> keras.Model:
    inputs = keras.Input(shape=(window_length, num_features), name="landmark_window")
    x = keras.layers.Conv1D(32, kernel_size=5, activation="relu", padding="same")(inputs)
    x = keras.layers.MaxPooling1D(pool_size=2)(x)
    x = keras.layers.Conv1D(64, kernel_size=5, activation="relu", padding="same")(x)
    x = keras.layers.GlobalAveragePooling1D()(x)
    x = keras.layers.Dense(32, activation="relu")(x)
    x = keras.layers.Dropout(0.3)(x)
    outputs = keras.layers.Dense(num_classes, activation="softmax", name="class_probs")(x)

    model = keras.Model(inputs, outputs)
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    return model


def export_tflite(model: keras.Model, output_path: str) -> None:
    """Keras 3 (TF 2.16+'s default) models convert to TFLite via a SavedModel
    export, not `TFLiteConverter.from_keras_model` directly — verified against
    a real TF 2.21.0 install, not assumed from documentation (see DECISIONS.md)."""
    import tempfile

    with tempfile.TemporaryDirectory() as tmp_dir:
        model.export(tmp_dir)
        converter = tf.lite.TFLiteConverter.from_saved_model(tmp_dir)
        tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)
