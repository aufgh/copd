"""真实模型接入位置（当前未启用）。

接入时要做的事只有三件，都在这个文件里：
1. 在 warmup() 里加载权重（服务启动时调用一次）。
2. 在 infer() 里消费 request.breathing.samples / request.cough.samples
   （已经是 16 kHz 单声道 float32，不需要再解码或重采样）。
3. 把模型输出填进 InferenceResult。

不需要改的：API 路由、schemas、Android 客户端、数据库结构。
启用方式：环境变量 COPD_ENGINE=real
"""

from __future__ import annotations

from app.inference.base import InferenceEngine, InferenceRequest, InferenceResult

REAL_VERSION = "real-0.0.0-placeholder"


class RealInferenceEngine(InferenceEngine):
    name = "real"
    version = REAL_VERSION

    def __init__(self, model_path: str | None = None) -> None:
        self.model_path = model_path
        self._model = None

    @property
    def engine_kind(self) -> str:
        return "real"

    def warmup(self) -> None:
        # TODO: 加载模型权重，例如
        # self._model = torch.jit.load(self.model_path).eval()
        return None

    def infer(self, request: InferenceRequest) -> InferenceResult:
        # TODO: 替换为真实推理
        #   breathing = request.breathing.samples   # np.float32, 16 kHz, mono
        #   cough = request.cough.samples
        #   feats = extract_features(breathing, cough)
        #   out = self._model(feats)
        raise NotImplementedError(
            "RealInferenceEngine 尚未实现。当前请使用 COPD_ENGINE=demo。"
        )
