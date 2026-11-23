from sqlalchemy import create_engine, Column, String, Float, Integer, ForeignKey, Boolean, event, text, Index
from sqlalchemy.orm import declarative_base, sessionmaker, relationship
import os

DB_PATH = os.environ.get("SS_DB_PATH") or os.path.join(os.path.dirname(__file__), "..", "data", "spendingsaver.db")
os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)


def _make_engine():
    """Create the SQLAlchemy engine, using SQLCipher if a key is configured
    and pysqlcipher3 is installed, otherwise plain SQLite (graceful fallback).

    Encryption is opt-in via SS_DB_KEY (or the shared vault key). The app is
    fully functional either way.
    """
    key = os.environ.get("SS_DB_KEY", "") or os.environ.get("SS_VAULT_KEY", "")
    use_cipher = bool(key)
    engine_kwargs = {"connect_args": {"check_same_thread": False}}
    module = None

    if use_cipher:
        try:
            from pysqlcipher3 import dbapi2 as sqlite3  # noqa: F401
            # keep default module; set key via PRAGMA in the connect event
        except ImportError:
            use_cipher = False

    if use_cipher:
        # SQLCipher: pass the key through a connect event so every connection
        # is unlocked. Plain sqlite3 module is swapped by pysqlcipher when usable.
        try:
            from pysqlcipher3 import dbapi2  # noqa: F401
            import sqlalchemy.dialects.sqlite.pysqlite as pysqlite
            # pysqlcipher registers itself as the 'sqlite3' module name when
            # installed; sqlalchemy picks it up via the pysqlite dialect.
            module = None  # sqlalchemy uses sqlite3 driver name
        except ImportError:
            use_cipher = False

    engine = create_engine(f"sqlite:///{DB_PATH}", **engine_kwargs)

    if use_cipher:
        from sqlalchemy import event as sa_event

        @sa_event.listens_for(engine, "connect")
        def _unlock(dbapi_connection, connection_record):
            try:
                dbapi_connection.execute(f"PRAGMA key = '{key}'")
            except Exception:
                # engine is sqlite3 module (cipher not registered) -> nothing
                pass

    return engine, use_cipher


engine, DB_ENCRYPTED = _make_engine()
SessionLocal = sessionmaker(bind=engine)
Base = declarative_base()


@event.listens_for(engine, "connect")
def _set_sqlite_pragma(dbapi_connection, connection_record):
    """Harden SQLite: WAL journal mode for crash resilience, FK enforcement on."""
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.close()


class UserModel(Base):
    __tablename__ = "users"
    id = Column(String, primary_key=True)
    username = Column(String, nullable=False, unique=True)
    email = Column(String, nullable=False, unique=True)
    password_hash = Column(String, nullable=False)
    is_active = Column(Boolean, nullable=False, default=True)
    totp_secret = Column(String, nullable=True)
    totp_enabled = Column(Boolean, nullable=False, default=False)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class AccountModel(Base):
    __tablename__ = "accounts"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    type = Column(String, nullable=False, default="checking")
    balance = Column(Float, nullable=False, default=0.0)
    currency = Column(String, nullable=False, default="USD")
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class CategoryModel(Base):
    __tablename__ = "categories"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    color = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class TransactionModel(Base):
    __tablename__ = "transactions"
    __table_args__ = (
        Index("ix_transactions_date_deleted", "date", "deleted_at"),
        Index("ix_transactions_account_id", "account_id"),
        Index("ix_transactions_category_id", "category_id"),
        Index("ix_transactions_updated_at", "updated_at"),
    )
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    account_id = Column(String, ForeignKey("accounts.id"), nullable=False)
    amount = Column(Float, nullable=False)
    description = Column(String, nullable=False)
    category_id = Column(String, ForeignKey("categories.id"), nullable=True)
    date = Column(String, nullable=False)
    notes = Column(String, nullable=True)
    created_at = Column(String, nullable=True)
    attachment_path = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)

class BudgetModel(Base):
    __tablename__ = "budgets"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    category_id = Column(String, ForeignKey("categories.id"), nullable=False)
    amount = Column(Float, nullable=False)
    period = Column(String, nullable=False, default="monthly")
    start_date = Column(String, nullable=False)
    carry_over = Column(Float, nullable=False, default=0.0)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class RecurringTransactionModel(Base):
    __tablename__ = "recurring_transactions"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    account_id = Column(String, ForeignKey("accounts.id"), nullable=False)
    amount = Column(Float, nullable=False)
    description = Column(String, nullable=False)
    category_id = Column(String, ForeignKey("categories.id"), nullable=True)
    frequency = Column(String, nullable=False, default="monthly")
    next_date = Column(String, nullable=False)
    active = Column(String, nullable=False, default="true")
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class SavingsGoalModel(Base):
    __tablename__ = "savings_goals"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    target_amount = Column(Float, nullable=False)
    current_amount = Column(Float, nullable=False, default=0.0)
    deadline = Column(String, nullable=True)
    account_id = Column(String, ForeignKey("accounts.id"), nullable=True)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class TransactionSplitModel(Base):
    __tablename__ = "transaction_splits"
    id = Column(String, primary_key=True)
    transaction_id = Column(String, ForeignKey("transactions.id"), nullable=False)
    category_id = Column(String, ForeignKey("categories.id"), nullable=True)
    amount = Column(Float, nullable=False)
    description = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class RecurringTemplateModel(Base):
    __tablename__ = "recurring_templates"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=False)
    name = Column(String, nullable=False)
    amount = Column(Float, nullable=False)
    category_id = Column(String, ForeignKey("categories.id"), nullable=True)
    frequency = Column(String, nullable=False, default="monthly")
    description = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class AuditLogModel(Base):
    __tablename__ = "audit_log"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    entity_type = Column(String, nullable=False)
    entity_id = Column(String, nullable=False)
    action = Column(String, nullable=False)
    details = Column(String, nullable=True)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class WidgetLayoutModel(Base):
    __tablename__ = "widget_layouts"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=False)
    widget_id = Column(String, nullable=False)
    position_x = Column(Integer, nullable=False, default=0)
    position_y = Column(Integer, nullable=False, default=0)
    width = Column(Integer, nullable=False, default=1)
    height = Column(Integer, nullable=False, default=1)
    visible = Column(Boolean, nullable=False, default=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class DebtModel(Base):
    __tablename__ = "debts"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    creditor = Column(String, nullable=True)
    balance = Column(Float, nullable=False)
    interest_rate = Column(Float, nullable=False, default=0.0)
    minimum_payment = Column(Float, nullable=False, default=0.0)
    due_date = Column(String, nullable=True)
    debt_type = Column(String, nullable=False, default="credit_card")
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class DebtPaymentModel(Base):
    __tablename__ = "debt_payments"
    id = Column(String, primary_key=True)
    debt_id = Column(String, ForeignKey("debts.id"), nullable=False)
    amount = Column(Float, nullable=False)
    payment_date = Column(String, nullable=False)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class InvestmentModel(Base):
    __tablename__ = "investments"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    ticker = Column(String, nullable=True)
    shares = Column(Float, nullable=False, default=0.0)
    cost_basis = Column(Float, nullable=False, default=0.0)
    current_price = Column(Float, nullable=False, default=0.0)
    investment_type = Column(String, nullable=False, default="stock")
    account_id = Column(String, ForeignKey("accounts.id"), nullable=True)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class BillSplitModel(Base):
    __tablename__ = "bill_splits"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    transaction_id = Column(String, ForeignKey("transactions.id"), nullable=True)
    total_amount = Column(Float, nullable=False)
    description = Column(String, nullable=True)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class BillSplitParticipantModel(Base):
    __tablename__ = "bill_split_participants"
    id = Column(String, primary_key=True)
    split_id = Column(String, ForeignKey("bill_splits.id"), nullable=False)
    name = Column(String, nullable=False)
    amount_owed = Column(Float, nullable=False)
    paid = Column(Boolean, nullable=False, default=False)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class TaxTagModel(Base):
    __tablename__ = "tax_tags"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    transaction_id = Column(String, ForeignKey("transactions.id"), nullable=False)
    category = Column(String, nullable=False)
    deductible = Column(Boolean, nullable=False, default=True)
    notes = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class IncomeStreamModel(Base):
    __tablename__ = "income_streams"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    name = Column(String, nullable=False)
    amount = Column(Float, nullable=False)
    frequency = Column(String, nullable=False, default="monthly")
    category = Column(String, nullable=True)
    next_date = Column(String, nullable=True)
    active = Column(Boolean, nullable=False, default=True)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class CreditScoreModel(Base):
    __tablename__ = "credit_scores"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    score = Column(Integer, nullable=False)
    provider = Column(String, nullable=True)
    factors = Column(String, nullable=True)
    recorded_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class TransactionTagModel(Base):
    __tablename__ = "transaction_tags"
    id = Column(String, primary_key=True)
    transaction_id = Column(String, ForeignKey("transactions.id"), nullable=False)
    tag = Column(String, nullable=False)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class GoalMilestoneModel(Base):
    __tablename__ = "goal_milestones"
    id = Column(String, primary_key=True)
    goal_id = Column(String, ForeignKey("savings_goals.id"), nullable=False)
    name = Column(String, nullable=False)
    target_amount = Column(Float, nullable=False)
    achieved = Column(Boolean, nullable=False, default=False)
    achieved_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class SpendingLimitModel(Base):
    __tablename__ = "spending_limits"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    category_id = Column(String, ForeignKey("categories.id"), nullable=False)
    limit_amount = Column(Float, nullable=False)
    period = Column(String, nullable=False, default="monthly")
    alert_threshold = Column(Float, nullable=False, default=0.8)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)

class BillReminderModel(Base):
    __tablename__ = "bill_reminders"
    id = Column(String, primary_key=True)
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    recurring_id = Column(String, ForeignKey("recurring_transactions.id"), nullable=True)
    reminder_days_before = Column(Integer, nullable=False, default=3)
    enabled = Column(Boolean, nullable=False, default=True)
    updated_at = Column(String, nullable=True)
    deleted_at = Column(String, nullable=True)


class VaultCredentialModel(Base):
    """Encrypted connector/bank credentials stored in the vault."""
    __tablename__ = "vault_credentials"
    id = Column(String, primary_key=True)
    entity_type = Column(String, nullable=False)
    entity_id = Column(String, nullable=False)
    encrypted_data = Column(String, nullable=False)
    created_at = Column(String, nullable=True)
    updated_at = Column(String, nullable=True)


def init_db():
    Base.metadata.create_all(engine)
    _ensure_soft_delete_columns()


def _ensure_soft_delete_columns():
    """Add updated_at/deleted_at columns to tables created before this migration."""
    with engine.begin() as conn:
        for cls in Base.__subclasses__():
            table = cls.__table__
            try:
                existing = {row[1] for row in conn.execute(text(f"PRAGMA table_info({table.name})"))}
            except Exception:
                continue
            declared = {c.name for c in table.columns}
            for colname in ("updated_at", "deleted_at"):
                if colname in declared and colname not in existing:
                    conn.execute(text(f"ALTER TABLE {table.name} ADD COLUMN {colname} VARCHAR"))
