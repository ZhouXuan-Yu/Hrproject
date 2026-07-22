from app.models.base import BaseModel
# Models are imported here so alembic can discover them
from app.models.iam import IamDept, IamPosition, IamUser
from app.models.infrastructure import File, TagDict, RecruitChannel, ScoreRule
from app.models.demand import DeptHC, RecruitDemand, DemandApproval
from app.models.candidate import Candidate, Resume, CandidateTagRel
from app.models.process import RecruitProcess, ResumeMatch, SearchLog
from app.models.interview import InterviewSlot, InterviewBook, InterviewRecord
from app.models.hire import HireEvent, Offer, Entry, OfferRemindLog
from app.models.internal import Employee, EmployeeTagRel, InternalMatchLog
from app.models.auxiliary import RecruitMailAccount, ChatLog, NotifyTemplate, AuditLog, ApiKeyConfig
